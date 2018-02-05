package dcraft.db.tables;

import dcraft.db.proc.BasicFilter;
import dcraft.db.proc.ExpressionResult;
import dcraft.db.proc.IComposer;
import dcraft.db.request.query.SelectFields;
import dcraft.db.util.ByteUtil;
import dcraft.hub.ResourceHub;
import dcraft.hub.op.OperatingContextException;
import dcraft.log.Logger;
import dcraft.schema.DataType;
import dcraft.schema.DbComposer;
import dcraft.schema.DbField;
import dcraft.schema.SchemaResource;
import dcraft.struct.ListStruct;
import dcraft.struct.RecordStruct;
import dcraft.struct.Struct;
import dcraft.struct.builder.BuilderStateException;
import dcraft.struct.builder.ICompositeBuilder;
import dcraft.struct.builder.ObjectBuilder;
import dcraft.util.StringUtil;

import java.util.function.Function;

public class TableUtil {

	static public RecordStruct getRecord(TablesAdapter db, String table, String id, SelectFields select) throws OperatingContextException {
		try {
			ICompositeBuilder out = new ObjectBuilder();

			TableUtil.writeRecord(out, db, table, id, select.getFields(), true, false);

			return (RecordStruct) out.toLocal();
		}
		catch (Exception x) {
			Logger.error("Unable to get record: " + x);
		}

		return null;
	}

	static public void writeRecord(ICompositeBuilder out,
							TablesAdapter db, String table, String id, ListStruct select,
							boolean compact, boolean skipWriteRec) throws OperatingContextException, BuilderStateException
	{
		if (!db.isCurrent(table, id))
			return;

		SchemaResource schema = ResourceHub.getResources().getSchema();

		// if select none then select all
		if (select.size() == 0) {
			for (DbField entry : schema.getDbFields(table))
				select.withItem(new RecordStruct().with("Field", entry.getName()));
		}

		if (!skipWriteRec)
			out.startRecord();

		for (Struct s : select.items()) {
			RecordStruct fld = (RecordStruct) s;

			out.field(fld.isFieldEmpty("Name") ? fld.getFieldAsString("Field") : fld.getFieldAsString("Name"));

			TableUtil.writeField(out, db, table, id, fld, compact);
		}

		if (!skipWriteRec)
			out.endRecord();
	}

	static public void writeField(ICompositeBuilder out, TablesAdapter db, String table, String id,
						   String field, boolean compact) throws BuilderStateException, OperatingContextException
	{
		RecordStruct fld = new RecordStruct()
				.with("Field", field);

		TableUtil.writeField(out, db, table, id, fld, compact);
	}

	static public void writeField(ICompositeBuilder out, TablesAdapter db, String table, String id,
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
				sp.writeField(out, db, table, id, field, myCompact);

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
					if (fid == null)
						out.value(null);
					else if (sfield != null)
						// if a single field the write out the field out "inlined"
						TableUtil.writeField(out, db, fktable, fid.toString(), sfield, myCompact);
					else
						// otherwise write the field out as a record within the list
						TableUtil.writeRecord(out, db, fktable, fid.toString(), subselect, myCompact, false);

					return true;
				}
				catch (Exception x) {
					Logger.error("Unable to write foreign record: " + x);
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
				db.traverseIndex(fktable, field.getFieldAsString("KeyField"), id, new BasicFilter() {
					@Override
					public ExpressionResult check(TablesAdapter adapter, Object id) throws OperatingContextException {
						if (foreignSink.apply(id))
							return ExpressionResult.accepted();

						return ExpressionResult.rejected();
					}
				});
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
						if (subselect != null) {
							Object value = db.getDynamicList(table, id, fname, subid.toString(), format);

							if (value != null) {
								foreignSink.apply(value);
								return true;
							}
						}
						else if (myCompact) {
							Object value = db.getDynamicList(table, id, fname, subid.toString());

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
	
	static public Object normalizeFormatRaw(String table, String id, String field, byte[] data, String format) {
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
	
	static public Object formatField(String table, String id, String field, Object value, String format) {
		if ("Tr".equals(format)) {
			// TODO translate $$tr^dcStrUtil("_enum_"_table_"_"_field_"_"_val)
		}
		
		// TODO format date/time to chrono
		
		// TODO format numbers to locale
		
		// TODO split? pad? custom format function?
		
		return value;
	}
	

	/*
		  ;
		  ;
		 format(table,field,val,format) i (table'["#")&(Domain'="") s table=table_"#"_Domain     ; support table instances
		  quit:format="" $$getTypeFor(^dcSchema($p(table,"#"),"Fields",field,"Type"))_val
		  quit:format="Tr" ScalarStr_$$tr^dcStrUtil("_enum_"_table_"_"_field_"_"_val)
		  ; TODO support date and number formatting, maybe str padding
		  quit ScalarStr_$$format^dcStrUtil(val,format)
		  ;
		  ;
		  ;
		 getTypeFor(type) quit:type="Time" ScalarTime
		  quit:type="Date" ScalarDate
		  quit:type="DateTime" ScalarDateTime
		  quit:type="Id" ScalarId
		  quit:type="Integer" ScalarInt
		  quit:type="Json" ScalarJson
		  quit:type="Decimal" ScalarDec
		  quit:type="BigInteger" ScalarBigInt
		  quit:type="BigDecimal" ScalarBigDec
		  quit:type="Number" ScalarNum
		  quit:type="Boolean" ScalarBool
		  quit:type="Binary" ScalarBin
		  quit:type="BigDateTime" ScalarBigDateTime
		  quit ScalarStr
		  ;
		  ;

*/

}
