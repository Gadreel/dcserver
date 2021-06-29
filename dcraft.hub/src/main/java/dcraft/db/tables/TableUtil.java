package dcraft.db.tables;

import dcraft.db.DatabaseAdapter;
import dcraft.db.ICallContext;
import dcraft.db.IRequestContext;
import dcraft.db.proc.*;
import dcraft.db.proc.filter.CurrentRecord;
import dcraft.db.proc.filter.Unique;
import dcraft.db.request.query.SelectFields;
import dcraft.db.request.update.DbRecordRequest;
import dcraft.db.util.ByteUtil;
import dcraft.hub.ResourceHub;
import dcraft.hub.op.IVariableAware;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
import dcraft.hub.op.OperationOutcomeStruct;
import dcraft.hub.time.BigDateTime;
import dcraft.log.Logger;
import dcraft.schema.*;
import dcraft.struct.DataUtil;
import dcraft.struct.ListStruct;
import dcraft.struct.RecordStruct;
import dcraft.struct.Struct;
import dcraft.struct.builder.BuilderStateException;
import dcraft.struct.builder.ICompositeBuilder;
import dcraft.struct.builder.ObjectBuilder;
import dcraft.util.StringUtil;
import dcraft.util.TimeUtil;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import static dcraft.db.Constants.DB_GLOBAL_INDEX_SUB;

public class TableUtil {

	static public void retireRecord(TablesAdapter db, String table, String id) throws OperatingContextException {
		db.retireRecord(table, id);
	}

	static public void reviveRecord(TablesAdapter db, String table, String id) throws OperatingContextException {
		db.reviveRecord(table, id);
	}

	static public String updateRecord(TablesAdapter db, DbRecordRequest request) throws OperatingContextException {
		RecordStruct params = request.buildParams();

		String table = params.getFieldAsString("Table");

		// ===========================================
		//  verify the fields
		// ===========================================

		RecordStruct fields = params.getFieldAsRecord("Fields");
		BigDateTime when = params.getFieldAsBigDateTime("When");

		// ===========================================
		//  run before trigger
		// ===========================================

		// it is possible for Id to be set by trigger (e.g. with domains)
		String id = params.getFieldAsString("Id");

		boolean isUpdate = StringUtil.isNotEmpty(id);

		db.executeTrigger(table, id, isUpdate ? "BeforeUpdate" : "BeforeInsert", fields);

		// check for errors here?
		if (! db.checkFieldsInternal(table, fields, params.getFieldAsString("Id"))) {
			return null;
		}

		// it is possible for Id to be set by trigger (e.g. with domains)
		id = params.getFieldAsString("Id");

		// TODO add db filter option
		//d runFilter("Insert" or "Update") quit:Errors  ; if any violations in filter then do not proceed

		// ===========================================
		//  create new id
		// ===========================================

		// don't create a new id during replication - not even for dcInsertRecord
		if (StringUtil.isEmpty(id)) {
			id = db.createRecord(table);

			if (StringUtil.isEmpty(id)) {
				return null;
			}

			params.with("Id", id);
		}

		// ===========================================
		//  do the data update
		// ===========================================
		db.setFieldsInternal(table, id, fields);

		// TODO don't want sets here at all if possible, they are not checked in checkFieldsInternal above and could ruin a create

		// ===========================================
		//  and set fields
		// ===========================================

		// TODO move to tables interface
		if (params.hasField("Sets")) {
			ListStruct sets = params.getFieldAsList("Sets");

			for (Struct set : sets.items()) {
				RecordStruct rset = (RecordStruct) set;

				String field = rset.getFieldAsString("Field");

				// make a copy
				List<String> lsubids = rset.getFieldAsList("Values").toStringList();
				List<String> othersubids = new ArrayList<>();

				db.traverseSubIds(table, id, field, new Function<Object,Boolean>() {
					@Override
					public Boolean apply(Object msub) {
						String suid = msub.toString();

						// if a value is already set, don't set it again
						if (! lsubids.remove(suid))
							othersubids.add(suid);

						return true;
					}
				});

				// Retire non matches
				for (String suid : othersubids) {
					// if present in our list then retire it
					db.setFields(table, id, new RecordStruct()
							.with(field, new RecordStruct()
									.with(suid, new RecordStruct()
											.with("Retired", true)
									)
							)
					);
				}

				// add any remaining - unmatched - suids
				for (String suid : lsubids) {
					// if present in our list then retire it
					db.setFields(table, id, new RecordStruct()
							.with(field, new RecordStruct()
									.with(suid, new RecordStruct()
											.with("Data", suid)
									)
							)
					);
				}
			}
		}

		// TODO make a record of everything for replication? or just let it figure it out?

		// ===========================================
		//  run after trigger
		// ===========================================
		db.executeTrigger(table, id, isUpdate ? "AfterUpdate" : "AfterInsert", fields);

		// TODO maybe check for errors here? originally exited on errors

		return id;
	}

	static public RecordStruct getRecord(TablesAdapter db, IVariableAware scope, String table, String id, SelectFields select) throws OperatingContextException {
		try {
			ICompositeBuilder out = new ObjectBuilder();

			TableUtil.writeRecord(out, db, RecordScope.of(scope), table, id, select.getFields(), true, false);

			return (RecordStruct) out.toLocal();
		}
		catch (Exception x) {
			Logger.error("Unable to get record: " + x);
		}

		return null;
	}

	static public void writeRecord(ICompositeBuilder out,
							TablesAdapter db, RecordScope scope, String table, String id, ListStruct select,
							boolean compact, boolean skipWriteRec) throws OperatingContextException, BuilderStateException
	{
		if (! db.isPresent(table, id))
			return;

		SchemaResource schema = ResourceHub.getResources().getSchema();
		
		TableView tableView = schema.getTableView(table);
		
		if (tableView == null) {
			Logger.error("Table does not exist: " + table);
			return;
		}

		// if select none then select all
		if (select.size() == 0) {
			for (DbField entry : tableView.getFields())
				select.withItem(new RecordStruct().with("Field", entry.getName()));
		}

		if (!skipWriteRec)
			out.startRecord();

		for (Struct s : select.items()) {
			RecordStruct fld = (RecordStruct) s;

			out.field(fld.isFieldEmpty("Name") ? fld.getFieldAsString("Field") : fld.getFieldAsString("Name"));

			TableUtil.writeField(out, db, scope, table, id, fld, compact);
		}

		if (!skipWriteRec)
			out.endRecord();
	}

	static public void writeField(ICompositeBuilder out, TablesAdapter db, RecordScope scope, String table, String id,
						   String field, boolean compact) throws BuilderStateException, OperatingContextException
	{
		RecordStruct fld = new RecordStruct()
				.with("Field", field);

		TableUtil.writeField(out, db, scope, table, id, fld, compact);
	}

	static public void writeField(ICompositeBuilder out, TablesAdapter db, RecordScope scope, String table, String id,
						   RecordStruct field, boolean compact) throws BuilderStateException, OperatingContextException
	{
		// some fields may request full details even if query is not in general
		if (compact && field.getFieldAsBooleanOrFalse("Full"))
			compact = false;

		boolean myCompact = compact;

		SchemaResource schema = ResourceHub.getResources().getSchema();

		if (! field.isFieldEmpty("Composer")) {
			DbComposer proc = schema.getDbComposer(field.getFieldAsString("Composer"));

			if (proc == null) {
				out.value(null);
				return;
			}

			IComposer sp = proc.getComposer();

			if (sp == null)
				out.value(null);
			else
				sp.writeField(out, db, scope, table, id, field, myCompact);

			return;
		}
		
		
		if (! field.isFieldEmpty("Filter")) {
			DbFilter proc = schema.getDbFilter(field.getFieldAsString("Filter"));
			
			if (proc == null) {
				out.value(null);
				return;
			}
			
			IFilter sp = proc.getFilter();
			
			if (sp == null)
				out.value(null);
			else
				out.value(sp.check(db, scope, table, id).accepted);
			
			return;
		}
		
		if (! field.isFieldEmpty("Value")) {
			out.value(field.getFieldAsAny("Value"));
			return;
		}

		String fname = field.getFieldAsString("Field");
		String format = field.getFieldAsString("Format");

		DbField fdef = schema.getDbField(table, fname);

		if (fdef == null) {
			out.value(null);
			return;
		}

		// for foreign key queries
		String ftable = fdef.getForeignKey();

		// for reverse foreign key queries
		if (! field.isFieldEmpty("Table"))
			ftable = field.getFieldAsString("Table");

		String fktable = ftable;

		// when set, subselect indicates that we want values from a foreign field
		ListStruct subselect = StringUtil.isNotEmpty(fktable) ? TableUtil.buildSubquery(field, fktable) : null;

		// when set, sfield indicates we want the foreign value inline with the other values
		RecordStruct sfield = ((subselect != null) && (subselect.size() == 1)) ? subselect.getItemAsRecord(0) : null;

		Function<Object,Boolean> foreignSink = new Function<Object,Boolean>() {
			@Override
			public Boolean apply(Object fid) {
				try {
					if ((fid == null) || ! db.isCurrent(fktable, fid.toString())) {
						out.value(null);
					}
					else if (sfield != null) {
						// if a single field the write out the field out "inlined"
						TableUtil.writeField(out, db, scope, fktable, fid.toString(), sfield, myCompact);
					}
					else {
						// otherwise write the field out as a record within the list
						TableUtil.writeRecord(out, db, RecordScope.of(scope), fktable, fid.toString(), subselect, myCompact, false);
					}

					return true;
				}
				catch (Exception x) {
					Logger.error("Unable to write foreign record: " + x);
				}

				return false;
			}
		};

		Function<Object,Boolean> foreignSinkCurrent = new Function<Object,Boolean>() {
			@Override
			public Boolean apply(Object fid) {
				try {
					if (fid != null) {
						// if a single field the write out the field out "inlined"
						return db.isCurrent(fktable, fid.toString());
					}
				}
				catch (Exception x) {
					Logger.error("Unable to check foreign record: " + x);
				}

				return false;
			}
		};

		if ("Id".equals(fname)) {
			// keep in mind that `id` is the "value"
			if ((subselect == null) || field.isFieldEmpty("KeyField")) {
				if (compact)
					out.value(id);
				else
					out.startRecord().field("Data", id).endRecord();
			}
			else {
				// write all records in reverse index within a List
				out.startList();
				db.traverseIndex(scope, fktable, field.getFieldAsString("KeyField"), id, CurrentRecord.current().withNested(new BasicFilter() {
					@Override
					public ExpressionResult check(TablesAdapter adapter, IVariableAware scope, String table, Object id) throws OperatingContextException {
						if (foreignSink.apply(id))
							return ExpressionResult.accepted();

						return ExpressionResult.rejected();
					}
				}));
				out.endList();
			}

			return;
		}

		String subid = field.getFieldAsString("SubId");

		if (StringUtil.isNotEmpty(subid) && fdef.isList()) {
			if (subselect != null)
				foreignSink.apply(db.getDynamicList(table, id, fname, subid, format));
			else if (compact)
				out.value(db.getDynamicList(table, id, fname, subid, format));
			else
				out.value(db.getDynamicListExtended(table, id, fname, subid, format));
		}
		// StaticList
		// TODO was -- else if (fdef.isList() || (fdef.isDynamic() && when == null)) { --- restore?
		else if (fdef.isList()) {
			out.startList();

			// keep in mind that `id` is the "value" in the index
			db.traverseSubIds(table, id, fname, new Function<Object,Boolean>() {
				@Override
				public Boolean apply(Object subid) {
					try {
						// don't output null values in this list - Extended might return null data but otherwise no nulls
						// GROUP
						if (field.hasField("KeyName")) {
							ListStruct subselect = TableUtil.buildSubquery(field, table);
							
							// every field in subselect should be a list item, part of the group
							for (int f = 0; f < subselect.size(); f++) {
								RecordStruct frec = subselect.getItemAsRecord(f);
								frec.with("SubId", subid);
							}
							
							out.startRecord();
							
							out.field(field.getFieldAsString("KeyName"), subid);

							// don't create a new scope, this is still the same record
							TableUtil.writeRecord(out, db, scope, table, id, subselect, true, true);
							
							out.endRecord();
							
							return true;
						}
						// SUBQUERY
						else if (subselect != null) {
							Object value = db.getDynamicList(table, id, fname, subid.toString(), format);
							
							if ((value != null) && foreignSinkCurrent.apply(value)) {
								foreignSink.apply(value);
								return true;
							}
						}
						else if (myCompact) {
							Object value = db.getDynamicList(table, id, fname, subid.toString(), format);

							if (value != null) {
								out.value(value);
								return true;
							}
						}
						else {
							Object value = db.getDynamicListExtended(table, id, fname, subid.toString(), format);

							if (value != null) {
								out.value(value);
								return true;
							}
						}
					}
					catch (Exception x) {
						Logger.error("Unable to write subid: " + x);
					}

					return false;
				}
			});

			out.endList();

			return;
		}
		// DynamicScalar
		else if (fdef.isDynamic()) {
			if (subselect != null)
				foreignSink.apply(db.getDynamicScalar(table, id, fname, format));
			else if (compact)
				out.value(db.getDynamicScalar(table, id, fname, format));
			else
				out.value(db.getDynamicScalarExtended(table, id, fname, format));
		}
		// StaticScalar
		else {
			if (subselect != null)
				foreignSink.apply(db.getStaticScalar(table, id, fname, format));
			else if (compact)
				out.value(db.getStaticScalar(table, id, fname, format));
			else
				out.value(db.getStaticScalarExtended(table, id, fname, format));
		}

		return;
	}

	// this works with FK queries (field and ftable params) and with ID Reverse query (just field param)
	static public ListStruct buildSubquery(RecordStruct field, String ftable) {
		if (StringUtil.isEmpty(ftable))
			return null;

		ListStruct subselect = field.getFieldAsList("Select");

		// if no subquery then use "ForeignField" instead
		if ((subselect == null) || (subselect.size() == 0)) {
			if (field.hasField("ForeignField"))
				return ListStruct.list(new RecordStruct()
						.with("Field", field.getFieldAsString("ForeignField"))
						.with("Format", field.getFieldAsString("Format"))
				);

			// TODO if no ForeignField then select all

			return null;
		}

		return subselect;
	}
	
	static public Object normalizeFormatRaw(String table, String id, String field, byte[] data, String format) throws OperatingContextException {
		if (data == null)
			return null;
		
		SchemaResource schemares = ResourceHub.getResources().getSchema();
		DbField schema = schemares.getDbField(table, field);
		
		if (schema == null) {
			Logger.error("Field not defined: " + table + " - " + field);
			return null;
		}
		
		DataType dtype = schemares.getType(schema.getTypeId());
		
		Object out = ByteUtil.extractValue(data, dtype);
		
		return TableUtil.formatField(table, id, field, out, format);
	}
	
	static public Object formatField(String table, String id, String field, Object value, String format) throws OperatingContextException {
		return DataUtil.format(value, format);
	}
	
	static public long countIndex(IRequestContext request, BigDateTime when, String table, String fname, Object val) throws OperatingContextException {
		String did = request.getTenant();
		
		long count = 0;
		
		SchemaResource schema = ResourceHub.getResources().getSchema();
		DbField ffdef = schema.getDbField(table, fname);
		
		if (ffdef == null)
			return count;
		
		DataType dtype = schema.getType(ffdef.getTypeId());
		
		if (dtype == null)
			return count;
		
		val = dtype.toIndex(val, "eng");	// TODO increase locale support
		
		try {
			return request.getInterface().getAsInteger(did, ffdef.getIndexName(), table, fname, val);
		}
		catch (Exception x) {
			Logger.error("traverseIndex error: " + x);
		}
		
		return count;
	}

	// if any trigger passed then all do - thus can add overrides at the application level
	static public boolean canWriteRecord(TablesAdapter db, String table, String id, String op, String tag, boolean fromrpc) throws OperatingContextException {
		RecordStruct context = RecordStruct.record()
				.with("Op", op)
				.with("Tag", tag)
				.with("FromRPC", fromrpc);

		return TableUtil.canWriteRecord(db, table, id, context);
	}

	static public boolean canWriteRecord(TablesAdapter db, String table, String id, RecordStruct context) throws OperatingContextException {
		boolean can = db.executeCanTrigger(table, id, "CheckWriteRecord", context);

		if (! can)
			Logger.error("Unable to update record.");

		return can;
	}

	// if any trigger passed then all do - thus can add overrides at the application level
	static public boolean canReadRecord(TablesAdapter db, String table, String id, String op, String tag, boolean fromrpc) throws OperatingContextException {
		RecordStruct context = RecordStruct.record()
				.with("Op", op)
				.with("Tag", tag)
				.with("FromRPC", fromrpc);

		return TableUtil.canReadRecord(db, table, id, context);
	}

	static public boolean canReadRecord(TablesAdapter db, String table, String id, RecordStruct context) throws OperatingContextException {
		boolean can = db.executeCanTrigger(table, id, "CheckReadRecord", context);

		if (! can)
			Logger.error("Unable to read record.");

		return can;
	}

	static public Unique traverseIndex(TablesAdapter db, String table, String field, String value) throws OperatingContextException {
		Unique collector = Unique.unique();

		db.traverseIndex(OperationContext.getOrThrow(), table, field, value, collector.withNested(CurrentRecord.current()));

		return collector;
	}
}
