package dcraft.db.tables;

import static dcraft.db.Constants.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;

import dcraft.db.DatabaseException;
import dcraft.db.ICallContext;
import dcraft.db.IRequestContext;
import dcraft.db.proc.*;
import dcraft.db.proc.expression.ExpressionUtil;
import dcraft.db.util.ByteUtil;
import dcraft.hub.ResourceHub;
import dcraft.hub.app.ApplicationHub;
import dcraft.hub.op.IVariableAware;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
import dcraft.hub.op.OperationMarker;
import dcraft.hub.time.BigDateTime;
import dcraft.locale.LocaleResource;
import dcraft.log.Logger;
import dcraft.schema.DataType;
import dcraft.schema.DbField;
import dcraft.schema.DbTrigger;
import dcraft.schema.SchemaHub;
import dcraft.schema.SchemaResource;
import dcraft.schema.TableView;
import dcraft.struct.FieldStruct;
import dcraft.struct.RecordStruct;
import dcraft.struct.Struct;
import dcraft.util.StringUtil;

public class TablesAdapter {
	static public TablesAdapter ofNow(IRequestContext request) {
		TablesAdapter adapter = new TablesAdapter();
		adapter.request = request;
		adapter.when = BigDateTime.nowDateTime();
		return adapter;
	}
	
	static public TablesAdapter of(IRequestContext request, BigDateTime when, boolean historical) {
		TablesAdapter adapter = new TablesAdapter();
		adapter.request = request;
		adapter.when = (when != null) ? when : BigDateTime.nowDateTime();
		adapter.historical = historical;
		return adapter;
	}
	
	protected IRequestContext request = null;
	protected BigDateTime when = null;
	protected boolean historical = false;
	
	// don't call for general code...
	protected TablesAdapter() {
	}

	public IRequestContext getRequest() {
		return this.request;
	}
	
	public BigDateTime getWhen() {
		return this.when;
	}
	
	public boolean isHistorical() {
		return this.historical;
	}
	
	public String createRecord(String table) {
		String nodeId = ApplicationHub.getNodeId();
		
		byte[] metakey = ByteUtil.buildKey("root", DB_GLOBAL_RECORD_META, table, "Id", nodeId);
		
		// use common Id's across all domains so that merge works and so that 
		// sys domain records (users) can be reused across domains
		try {
			Long id = this.request.getInterface().inc(metakey);
			
			String nid = nodeId + "_" + StringUtil.leftPad(id.toString(), 15, '0');
			
			this.executeTrigger(table, nid,"AfterCreate", null);
			
			return nid;
		}
		catch (Exception x) {
			Logger.error("Unable to create record id: " + x);
			return null;
		}
	}
	
	public boolean checkFieldsInternal(String table, RecordStruct fields, String inId) throws OperatingContextException {
		BiConsumer<DbField,RecordStruct> fieldChecker = new BiConsumer<DbField,RecordStruct>() {
			@Override
			public void accept(DbField schema, RecordStruct data) {
				boolean retired = data.getFieldAsBooleanOrFalse("Retired");
				
				if (retired) {
					if (schema.isRequired()) 
						Logger.error("Field cannot be retired: " + table + " - " + schema.getName());
					
					return;
				}
				
				// validate data type
				Struct value = data.getField("Data");
				
				if (value == null) {
					if (schema.isRequired()) 
						Logger.error("Field cannot be null: " + table + " - " + schema.getName());
					
					return;
				}
				
				Struct cor = SchemaHub.normalizeValidateType(true, false, value, schema.getTypeId());
				
				if (cor == null) 
					return;
				
				data.with("Data", cor);
				
				Object cValue = Struct.objectToCore(cor);		// %%% this was questionable, used to be value not cor
				
				if (cValue == null) {
					if (schema.isRequired()) 
						Logger.error("Field cannot be null: " + table + " - " + schema.getName());
					
					return;
				}
				
				if (! schema.isUnique())
					return;
				
				try {
					// make sure value is unique - null for when is fine because uniqueness is not time bound
					Object id = TablesAdapter.this.firstInIndex(table, schema.getName(), cValue, false);
					
					// if we are a new record
					if (inId == null) {
						if (id != null) {
							Logger.errorTr(50018, table, schema.getName());
							return;
						}
						
					}
					// if we are not a new record
					else if (id != null) {
						if (!inId.equals(id)) {
							Logger.errorTr(50017, table, schema.getName());
							return;
						}
					}
				}
				catch (OperatingContextException x) {
					Logger.error("Error check if field already in use, must be unique: " + table + " - " + schema.getName());
					return;
				}
			}
		}; 
		
		SchemaResource schemares = ResourceHub.getResources().getSchema();
		
		TableView tableView = schemares.getTableView(table);
		
		if (tableView == null) {
			Logger.error("Table does not exist: " + table);
			return false;
		}
		
		try (OperationMarker om = OperationMarker.create()) {
			// checking incoming fields for type correctness, uniqueness and requiredness
			for (FieldStruct field : fields.getFields()) {
				String fname = field.getName();
				
				try {
					DbField schema = tableView.getField(fname);
					
					if (schema == null) {
						Logger.error("Field not defined: " + table + " - " + fname);
						return false;
					}
					
					// --------------------------------------
					// StaticScalar handling - Data or Retired (true) not both
					// --------------------------------------
					if (! schema.isList() && ! schema.isDynamic()) {
						fieldChecker.accept(schema, (RecordStruct) field.getValue());
					}
					// --------------------------------------
					// StaticList handling
					// DynamicScalar handling
					// DynamicList handling
					// --------------------------------------
					else {
						for (FieldStruct subid : ((RecordStruct) field.getValue()).getFields())
							fieldChecker.accept(schema, (RecordStruct) subid.getValue());
					}
				}
				catch (Exception x) {
					Logger.error("Error checking field: " + fname);
					return false;
				}
			}
			
			if (om.hasErrors()) {
				Logger.error("Error checking fields, cannot continue.");
				return false;
			}
		}
		catch (Exception x) {
			Logger.error("Error checking fields: " + x);
			return false;
		}
		
		// if we are a new record, check that we have all the required fields
		if (inId == null) {
			for (DbField schema : tableView.getFields()) {
				if (! schema.isRequired())
					continue;
				
				// all we need to do is check if the field is present, the checks above have already shown
				// that fields present pass the required check
				if (! fields.hasField(schema.getName())) {
					Logger.error("Field missing but required: " + table + " - " + schema.getName());
					return false;
				}
			}			
		}
		
		return true;
	}
	
	public boolean setFields(String table, String id, RecordStruct fields) throws OperatingContextException {
		if (! this.checkFieldsInternal(table, fields, id))
			return false;
		
		return this.setFieldsInternal(table, id, fields);
	}
	
	public boolean setFieldsInternal(String table, String id, RecordStruct fields) throws OperatingContextException {
		String did = this.request.getTenant();
		
		try {
			boolean recPresent = this.request.getInterface().hasAny(did, DB_GLOBAL_RECORD, table, id);
			
			if (!recPresent) {
				byte[] metacnt = ByteUtil.buildKey("root", DB_GLOBAL_RECORD_META, table, "Count");
				
				// update the record count
				this.request.getInterface().inc(metacnt);
			}
		}
		catch (Exception x) {
			Logger.error("Unable to check/update record count: " + x);
			return false;
		}

		SchemaResource schemares = ResourceHub.getResources().getSchema();
		// must use tenant since database is shared by sites
		LocaleResource locale = OperationContext.getOrThrow().getTenant().getResources().getLocale();

		// --------------------------------------------------
		//   index updates herein may have race condition issues with the value counts
		//   this is ok as counts are just used for suggestions anyway
		//   TODO - DB cleanup on all indexes
		//   TODO - DB cleanup on all index value counts
		// --------------------------------------------------
		
		for (FieldStruct field : fields.getFields()) {
			String fname = field.getName();
			
			boolean auditDisabled = this.request.getInterface().isAuditDisabled();
			BigDecimal stamp = this.request.getStamp();
			
			try {
				DbField schema = schemares.getDbField(table, fname);
				
				if (schema == null) {
					Logger.error("Field not defined: " + table + " - " + fname);
					return false;
				}
				
				if (!schema.isAudit()) {
					auditDisabled = true;
					stamp = BigDecimal.ZERO;
				}				
				
				DataType dtype = schemares.getType(schema.getTypeId()); 

				// --------------------------------------
				// StaticScalar handling - Data or Retired (true) not both
				//
				//   fields([field name],"Data") = [value]
				//   fields([field name],"Lang") = [value]
				//   fields([field name],"Tags") = [value]
				//   fields([field name],"UpdateOnly") = [value]
				//   fields([field name],"Retired") = [value]
				// --------------------------------------
				if (! schema.isList() && ! schema.isDynamic()) {
					RecordStruct data = (RecordStruct) field.getValue();
					
					Object newValue = Struct.objectToCore(data.getField("Data"));
					String tags = data.getFieldAsString("Tags");
					boolean retired = data.getFieldAsBooleanOrFalse("Retired");
					boolean updateOnly = data.getFieldAsBooleanOrFalse("UpdateOnly");
					String lang = data.getFieldAsString("Lang", locale.getDefaultLocale());
					Object newIdxValue = dtype.toIndex(newValue, lang);
					
					boolean isTenantLang = lang.equalsIgnoreCase(locale.getDefaultLocale());

					// find the first, newest, stamp 
					byte[] newerStamp = this.request.getInterface().nextPeerKey( did, DB_GLOBAL_RECORD, table, id, fname, null);
					
					boolean hasNewer = false;
					
					if (newerStamp != null) {
						BigDecimal newStamp = Struct.objectToDecimal(ByteUtil.extractValue(newerStamp));
						// if we come after newer then we are older info, there is newer
						hasNewer = (stamp.compareTo(newStamp) > 0);
					}
					
					// find the next, older, stamp after current
					byte[] olderStamp = this.request.getInterface().getOrNextPeerKey(did, DB_GLOBAL_RECORD, table, id, fname, stamp);
					boolean oldIsSet = false;
					boolean oldIsRetired = false;
					Object oldValue = null;
					Object oldIdxValue = null;

					if (olderStamp != null) {
						BigDecimal oldStamp = Struct.objectToDecimal(ByteUtil.extractValue(olderStamp));
						
						// try to get the data if any - note retired fields have no data
						if (oldStamp != null) {
							oldIsRetired = this.request.getInterface().getAsBooleanOrFalse(did, DB_GLOBAL_RECORD, table, id, fname, oldStamp, "Retired");
							oldIsSet = this.request.getInterface().isSet(did, DB_GLOBAL_RECORD, table, id, fname, oldStamp, "Data");
							
							if (oldIsSet) 
								oldValue = this.request.getInterface().get(did, DB_GLOBAL_RECORD, table, id, fname, oldStamp, "Data");
							
							if (schema.isIndexed()) {
								oldIdxValue = this.request.getInterface().get(did, DB_GLOBAL_RECORD, table, id, fname, oldStamp, "Index");
								
								if (oldIdxValue == null)
									oldIdxValue = oldValue;
							}
						}
					}
					
					boolean effectivelyEqual = (retired && oldIsRetired) || ((oldValue == null) && (newValue == null)) || ((oldValue != null) && oldValue.equals(newValue));
					
					if (updateOnly && effectivelyEqual) 
						continue;

					// set either retired or data, not both
					if (retired) {
						if (oldIsSet && auditDisabled) {
							this.request.getInterface().kill(did, DB_GLOBAL_RECORD, table, id, fname, stamp, "Data");
							this.request.getInterface().kill(did, DB_GLOBAL_RECORD, table, id, fname, stamp, "Search");
							this.request.getInterface().kill(did, DB_GLOBAL_RECORD, table, id, fname, stamp, "Index");
							this.request.getInterface().kill(did, DB_GLOBAL_RECORD, table, id, fname, stamp, "Lang");
						}
						
						this.request.getInterface().set(did, DB_GLOBAL_RECORD, table, id, fname, stamp, "Retired", retired);
					}
					else {						
						if (auditDisabled)
							this.request.getInterface().kill(did, DB_GLOBAL_RECORD, table, id, fname, stamp, "Retired");
						
						this.request.getInterface().set(did, DB_GLOBAL_RECORD, table, id, fname, stamp, "Data", newValue);

						if (dtype.isSearchable()) {
							this.request.getInterface().set(did, DB_GLOBAL_RECORD, table, id, fname, stamp, "Search", dtype.toSearch(newValue, lang));
							this.request.getInterface().set(did, DB_GLOBAL_RECORD, table, id, fname, stamp, "Index", newIdxValue);
							
							if (! isTenantLang)
								this.request.getInterface().set(did, DB_GLOBAL_RECORD, table, id, fname, stamp, "Lang", lang);
						}
						else if (auditDisabled) {
							this.request.getInterface().kill(did, DB_GLOBAL_RECORD, table, id, fname, stamp, "Search");
							this.request.getInterface().kill(did, DB_GLOBAL_RECORD, table, id, fname, stamp, "Index");
							this.request.getInterface().kill(did, DB_GLOBAL_RECORD, table, id, fname, stamp, "Lang");
						}
					}
					
					// add tags if any - ok even if retired
					if (StringUtil.isNotEmpty(tags))
						this.request.getInterface().set(did, DB_GLOBAL_RECORD, table, id, fname, stamp, "Tags", tags);
					else if (auditDisabled)
						this.request.getInterface().kill(did, DB_GLOBAL_RECORD, table, id, fname, stamp, "Tags");
						
					// don't bother with the indexes if not configured
					// or if there is a newer value for this field already set
					if (! schema.isIndexed() || hasNewer || effectivelyEqual)
						continue;
					
					if (oldIsSet && ! oldIsRetired && (oldIdxValue != null)) {
						// decrement index count for the old value
						// remove the old index value
						this.request.getInterface().dec(did, DB_GLOBAL_INDEX, table, fname, oldIdxValue);
						this.request.getInterface().kill(did, DB_GLOBAL_INDEX, table, fname, oldIdxValue, id);
					}
					
					if (! retired && (newIdxValue != null)) {
						// increment index count
						// set the new index new
						this.request.getInterface().inc(did, DB_GLOBAL_INDEX, table, fname, newIdxValue);
						this.request.getInterface().set(did, DB_GLOBAL_INDEX, table, fname, newIdxValue, id, null);
					}
					
					continue;
				}
				
				// --------------------------------------
				//
				// Handling for other types
				//
				// StaticList handling
				//   fields([field name],sid,"Data") = [value]
				//   fields([field name],sid,"Lang") = [value]
				//   fields([field name],sid,"Tags") = [value]			|value1|value2|etc...
				//   fields([field name],sid,"Retired") = [value]			|value1|value2|etc...
				//
				// DynamicScalar handling
				//   fields([field name],sid,"Data") = [value]
				//   fields([field name],sid,"Lang") = [value]
				//   fields([field name],sid,"From") = [value]			null means always was
				//   fields([field name],sid,"Tags") = [value]
				//   fields([field name],sid,"Retired") = [value]			|value1|value2|etc...
				//
				// DynamicList handling
				//   fields([field name],sid,"Data") = [value]
				//   fields([field name],sid,"Lang") = [value]
				//   fields([field name],sid,"From") = [value]			null means always was
				//   fields([field name],sid,"To") = [value]				null means always will be
				//   fields([field name],sid,"Tags") = [value]
				//   fields([field name],sid,"Retired") = [value]			|value1|value2|etc...
				// --------------------------------------
				
				for (FieldStruct subid : ((RecordStruct) field.getValue()).getFields()) {
					String sid = subid.getName();
					
					RecordStruct data = (RecordStruct) subid.getValue();
					
					Object newValue = Struct.objectToCore(data.getField("Data"));
					String tags = data.getFieldAsString("Tags");
					String lang = data.getFieldAsString("Lang", locale.getDefaultLocale());
					Object newIdxValue = dtype.toIndex(newValue, lang);
					
					boolean isTenantLang = lang.equalsIgnoreCase(locale.getDefaultLocale());
					boolean retired = data.getFieldAsBooleanOrFalse("Retired");
					boolean updateOnly = data.getFieldAsBooleanOrFalse("UpdateOnly");
					
					BigDateTime from = data.getFieldAsBigDateTime("From");
					BigDateTime to = data.getFieldAsBigDateTime("To");
					
					// find the first, newest, stamp 
					byte[] newerStamp = this.request.getInterface().nextPeerKey(did, DB_GLOBAL_RECORD, table, id, fname, sid, null);
					
					boolean hasNewer = false;
					
					if (newerStamp != null) {
						BigDecimal newStamp = Struct.objectToDecimal(ByteUtil.extractValue(newerStamp));
						// if we come after newer then we are older info, there is newer
						hasNewer = (stamp.compareTo(newStamp) > 0);
					}
					
					// find the next, older, stamp after current
					byte[] olderStamp = this.request.getInterface().getOrNextPeerKey(did, DB_GLOBAL_RECORD, table, id, fname, sid, stamp);
					BigDecimal oldStamp = null;
					boolean oldIsSet = false;
					boolean oldIsRetired = false;
					Object oldValue = null;
					Object oldIdxValue = null;

					if (olderStamp != null) {
						oldStamp = Struct.objectToDecimal(ByteUtil.extractValue(olderStamp));
						
						// try to get the data if any - note retired fields have no data
						if (oldStamp != null) {
							oldIsRetired = this.request.getInterface().getAsBooleanOrFalse(did, DB_GLOBAL_RECORD, table, id, fname, sid, oldStamp, "Retired");
							oldIsSet = this.request.getInterface().isSet(did, DB_GLOBAL_RECORD, table, id, fname, sid, oldStamp, "Data");
							
							if (oldIsSet) 
								oldValue = this.request.getInterface().get(did, DB_GLOBAL_RECORD, table, id, fname, sid, oldStamp, "Data");
							
							if (schema.isIndexed()) {
								oldIdxValue = this.request.getInterface().get(did, DB_GLOBAL_RECORD, table, id, fname, sid, oldStamp, "Index");
								
								if (oldIdxValue == null)
									oldIdxValue = oldValue;
							}
						}
					}
					
					boolean effectivelyEqual = (retired && oldIsRetired) || ((oldValue == null) && (newValue == null)) || ((oldValue != null) && oldValue.equals(newValue));
					
					if (updateOnly && effectivelyEqual) 
						// TODO for dynamic scalar (only) look at previous value (different sid) and skip if that has same value
						continue;
					
					// set either retired or data, not both
					if (retired) {
						// if we are retiring then get rid of old value
						if (auditDisabled && oldIsSet) {
							this.request.getInterface().kill(did, DB_GLOBAL_RECORD, table, id, fname, sid, stamp, "Data");
							this.request.getInterface().kill(did, DB_GLOBAL_RECORD, table, id, fname, sid, stamp, "Search");
							this.request.getInterface().kill(did, DB_GLOBAL_RECORD, table, id, fname, sid, stamp, "Index");
							this.request.getInterface().kill(did, DB_GLOBAL_RECORD, table, id, fname, sid, stamp, "Lang");
						}
						
						this.request.getInterface().set(did, DB_GLOBAL_RECORD, table, id, fname, sid, stamp, "Retired", retired);
					}
					else {
						// if we are not retiring then get rid of old Retired just in case it was set before
						if (auditDisabled)
							this.request.getInterface().kill(did, DB_GLOBAL_RECORD, table, id, fname, sid, stamp, "Retired");
						
						this.request.getInterface().set(did, DB_GLOBAL_RECORD, table, id, fname, sid, stamp, "Data", newValue);

						if (dtype.isSearchable()) {
							this.request.getInterface().set(did, DB_GLOBAL_RECORD, table, id, fname, sid, stamp, "Search", dtype.toSearch(newValue, lang));
							this.request.getInterface().set(did, DB_GLOBAL_RECORD, table, id, fname, sid, stamp, "Index", newIdxValue);
							
							if (! isTenantLang)
								this.request.getInterface().set(did, DB_GLOBAL_RECORD, table, id, fname, sid, stamp, "Lang", lang);
						}
						else if (auditDisabled) {
							this.request.getInterface().kill(did, DB_GLOBAL_RECORD, table, id, fname, sid, stamp, "Search");
							this.request.getInterface().kill(did, DB_GLOBAL_RECORD, table, id, fname, sid, stamp, "Index");
							this.request.getInterface().kill(did, DB_GLOBAL_RECORD, table, id, fname, sid, stamp, "Lang");
						}
					}
					
					// add tags if any - ok even if retired
					if (StringUtil.isNotEmpty(tags))
						this.request.getInterface().set(did, DB_GLOBAL_RECORD, table, id, fname, sid, stamp, "Tags", tags);
					else if (auditDisabled)
						this.request.getInterface().kill(did, DB_GLOBAL_RECORD, table, id, fname, sid, stamp, "Tags");
					
					if (from != null)
						this.request.getInterface().set(did, DB_GLOBAL_RECORD, table, id, fname, sid, stamp, "From", from);
					else if (auditDisabled)
						this.request.getInterface().kill(did, DB_GLOBAL_RECORD, table, id, fname, sid, stamp, "From");
					
					if (to != null)
						this.request.getInterface().set(did, DB_GLOBAL_RECORD, table, id, fname, sid, stamp, "To", to);
					else if (auditDisabled)
						this.request.getInterface().kill(did, DB_GLOBAL_RECORD, table, id, fname, sid, stamp, "To");
					
					// don't bother with the indexes if not configured
					// or if there is a newer value for this field already set
					if (! schema.isIndexed() || hasNewer || effectivelyEqual)
						continue;
					
					if (oldIsSet && ! oldIsRetired && (oldIdxValue != null)) {
						// decrement index count for the old value
						// remove the old index value
						this.request.getInterface().dec(did, DB_GLOBAL_INDEX_SUB, table, fname, oldIdxValue);
						this.request.getInterface().kill(did, DB_GLOBAL_INDEX_SUB, table, fname, oldIdxValue, id, sid);
					}
					
					if (! retired && (newIdxValue != null)) {
						String range = null;
						
						if (from != null)
							range = from.toString();
						
						if (to != null) {
							if (range == null)
								range = ":" + to.toString();
							else
								range += ":" + to.toString();
						}

						// increment index count
						// set the new index new
						this.request.getInterface().inc(did, DB_GLOBAL_INDEX_SUB, table, fname, newIdxValue);
						this.request.getInterface().set(did, DB_GLOBAL_INDEX_SUB, table, fname, newIdxValue, id, sid, range);
					}
					
					continue;
				}
			}
			catch (Exception x) {
				Logger.error("Error updating field: " + fname);
				return false;
			}
		}
		
		return true;
	}
	
	public void indexCleanRecords(IVariableAware scope, TableView tableschema) throws OperatingContextException {
		this.traverseRecords(scope, tableschema.getName(), new BasicFilter() {
			@Override
			public ExpressionResult check(TablesAdapter adapter, IVariableAware scope, String table, Object val) throws OperatingContextException {
				OperationContext.getOrThrow().touch();
				
				String id = val.toString();
				
				Logger.info(" - Record: " + id);
				
				TablesAdapter.this.indexCleanFields(tableschema, id);
				
				return ExpressionResult.accepted();
			}
		});
	}
	
	public void indexCleanFields(TableView table, String id) throws OperatingContextException {
		for (DbField field : table.getFields()) {
			//if (! field.isIndexed())
			//	continue;
			
			// TODO if debug
			//Logger.info("   - Field: " + field.getName());
			
			this.indexCleanField(table, field, id);
		}
	}
	
	public void indexCleanField(TableView tableschema, DbField schema, String id) throws OperatingContextException {
		String did = this.request.getTenant();

		if (schema == null) {
			Logger.error("Field not defined: " + tableschema.getName());
			return;
		}

		SchemaResource schemares = ResourceHub.getResources().getSchema();
		DataType dtype = schemares.getType(schema.getTypeId());
		
		LocaleResource locale = OperationContext.getOrThrow().getTenant().getResources().getLocale();
		
		// --------------------------------------------------
		//   index updates herein may have race condition issues with the value counts
		//   this is ok as counts are just used for suggestions anyway
		// --------------------------------------------------
		
		String fname = schema.getName();

		try {
			// --------------------------------------
			// StaticScalar handling - Data or Retired (true) not both
			//
			//   fields([field name],"Data") = [value]
			//   fields([field name],"Lang") = [value]
			//   fields([field name],"Tags") = [value]
			//   fields([field name],"UpdateOnly") = [value]
			//   fields([field name],"Retired") = [value]
			// --------------------------------------
			if (! schema.isList() && ! schema.isDynamic()) {
				// find the first, newest, stamp
				byte[] stampraw = this.request.getInterface().nextPeerKey( did, DB_GLOBAL_RECORD, tableschema.getName(), id, fname, null);
				
				if (stampraw == null)
					return;
				
				BigDecimal stamp = Struct.objectToDecimal(ByteUtil.extractValue(stampraw));
				
				if (stamp == null)
					return;
				
				// try to get the data if any - note retired fields have no data
				boolean isRetired = this.request.getInterface().getAsBooleanOrFalse(did, DB_GLOBAL_RECORD, tableschema.getName(), id, fname, stamp, "Retired");
				boolean isSet = ! isRetired && this.request.getInterface().isSet(did, DB_GLOBAL_RECORD, tableschema.getName(), id, fname, stamp, "Data");
				
				Object value = isSet ? this.request.getInterface().get(did, DB_GLOBAL_RECORD, tableschema.getName(), id, fname, stamp, "Data") : null;
				
				String lang = locale.getDefaultLocale();
				
				if (this.request.getInterface().isSet(did, DB_GLOBAL_RECORD, tableschema.getName(), id, fname, stamp, "Lang"))
					lang = Struct.objectToString(this.request.getInterface().get(did, DB_GLOBAL_RECORD, tableschema.getName(), id, fname, stamp, "Lang"));
				
				boolean isTenantLang = lang.equalsIgnoreCase(locale.getDefaultLocale());

				// same stamp - just a possible value in the index
				Object oldIdxValue = this.request.getInterface().get(did, DB_GLOBAL_RECORD, tableschema.getName(), id, fname, stamp, "Index");
				
				if (oldIdxValue == null)
					oldIdxValue = dtype.toIndex(value, lang);

				Object newIdxValue = isRetired ? null : dtype.toIndex(value, lang);
				
				// set either retired or data, not both
				if (isRetired) {
					this.request.getInterface().kill(did, DB_GLOBAL_RECORD, tableschema.getName(), id, fname, stamp, "Data");
					this.request.getInterface().kill(did, DB_GLOBAL_RECORD, tableschema.getName(), id, fname, stamp, "Search");
					this.request.getInterface().kill(did, DB_GLOBAL_RECORD, tableschema.getName(), id, fname, stamp, "Index");
					this.request.getInterface().kill(did, DB_GLOBAL_RECORD, tableschema.getName(), id, fname, stamp, "Lang");
				}
				else if (dtype.isSearchable()) {
					this.request.getInterface().set(did, DB_GLOBAL_RECORD, tableschema.getName(), id, fname, stamp, "Search", dtype.toSearch(value, lang));
					this.request.getInterface().set(did, DB_GLOBAL_RECORD, tableschema.getName(), id, fname, stamp, "Index", newIdxValue);
					
					if (! isTenantLang)
						this.request.getInterface().set(did, DB_GLOBAL_RECORD, tableschema.getName(), id, fname, stamp, "Lang", lang);
					else
						this.request.getInterface().kill(did, DB_GLOBAL_RECORD, tableschema.getName(), id, fname, stamp, "Lang");
				}
				else {
					this.request.getInterface().kill(did, DB_GLOBAL_RECORD, tableschema.getName(), id, fname, stamp, "Search");
					this.request.getInterface().kill(did, DB_GLOBAL_RECORD, tableschema.getName(), id, fname, stamp, "Index");
					this.request.getInterface().kill(did, DB_GLOBAL_RECORD, tableschema.getName(), id, fname, stamp, "Lang");
				}

				/* not good because prevents set from clean global
				boolean effectivelyEqual = ((oldIdxValue == null) && (newIdxValue == null)) || ((oldIdxValue != null) && oldIdxValue.equals(newIdxValue));

				if (effectivelyEqual)
					return;
				*/

				if ((oldIdxValue != null) && this.request.getInterface().isSet(did, DB_GLOBAL_INDEX, tableschema.getName(), fname, oldIdxValue, id)) {
					// decrement index count for the old value
					// remove the old index value
					this.request.getInterface().dec(did, DB_GLOBAL_INDEX, tableschema.getName(), fname, oldIdxValue);
					this.request.getInterface().kill(did, DB_GLOBAL_INDEX, tableschema.getName(), fname, oldIdxValue, id);
				}
				
				// don't bother with the indexes if not configured
				// or if there is a newer value for this field already set
				if (schema.isIndexed() && (newIdxValue != null)) {
					// increment index count
					// set the new index new
					this.request.getInterface().inc(did, DB_GLOBAL_INDEX, tableschema.getName(), fname, newIdxValue);
					this.request.getInterface().set(did, DB_GLOBAL_INDEX, tableschema.getName(), fname, newIdxValue, id, null);
				}
				
				return;
			}
			
			// --------------------------------------
			//
			// Handling for other types
			//
			// StaticList handling
			//   fields([field name],sid,"Data") = [value]
			//   fields([field name],sid,"Lang") = [value]
			//   fields([field name],sid,"Tags") = [value]			|value1|value2|etc...
			//   fields([field name],sid,"Retired") = [value]			|value1|value2|etc...
			//
			// DynamicScalar handling
			//   fields([field name],sid,"Data") = [value]
			//   fields([field name],sid,"Lang") = [value]
			//   fields([field name],sid,"From") = [value]			null means always was
			//   fields([field name],sid,"Tags") = [value]
			//   fields([field name],sid,"Retired") = [value]			|value1|value2|etc...
			//
			// DynamicList handling
			//   fields([field name],sid,"Data") = [value]
			//   fields([field name],sid,"Lang") = [value]
			//   fields([field name],sid,"From") = [value]			null means always was
			//   fields([field name],sid,"To") = [value]				null means always will be
			//   fields([field name],sid,"Tags") = [value]
			//   fields([field name],sid,"Retired") = [value]			|value1|value2|etc...
			// --------------------------------------
			// find the first, newest, stamp
			
			byte[] subraw = this.request.getInterface().nextPeerKey( did, DB_GLOBAL_RECORD, tableschema.getName(), id, fname, null);
			
			while (subraw != null) {
				String sid = Struct.objectToString(ByteUtil.extractValue(subraw));
				
				// error
				if (sid == null)
					return;

				// STAY IN LOOP until we run out of sub ids

				// find the first, newest, stamp
				byte[] stampraw = this.request.getInterface().nextPeerKey(did, DB_GLOBAL_RECORD, tableschema.getName(), id, fname, sid, null);
				
				if (stampraw != null) {
					BigDecimal stamp = Struct.objectToDecimal(ByteUtil.extractValue(stampraw));

					if (stamp != null) {
						// try to get the data if any - note retired fields have no data
						boolean isRetired = this.request.getInterface().getAsBooleanOrFalse(did, DB_GLOBAL_RECORD, tableschema.getName(), id, fname, sid, stamp, "Retired");
						boolean isSet = !isRetired && this.request.getInterface().isSet(did, DB_GLOBAL_RECORD, tableschema.getName(), id, fname, sid, stamp, "Data");

						Object value = isSet ? this.request.getInterface().get(did, DB_GLOBAL_RECORD, tableschema.getName(), id, fname, sid, stamp, "Data") : null;

						String lang = locale.getDefaultLocale();

						if (this.request.getInterface().isSet(did, DB_GLOBAL_RECORD, tableschema.getName(), id, fname, sid, stamp, "Lang"))
							lang = Struct.objectToString(this.request.getInterface().get(did, DB_GLOBAL_RECORD, tableschema.getName(), id, fname, sid, stamp, "Lang"));

						boolean isTenantLang = lang.equalsIgnoreCase(locale.getDefaultLocale());

						Object oldIdxValue = this.request.getInterface().get(did, DB_GLOBAL_RECORD, tableschema.getName(), id, fname, sid, stamp, "Index");

						if (oldIdxValue == null)
							oldIdxValue = dtype.toIndex(value, lang);

						Object newIdxValue = isRetired ? null : dtype.toIndex(value, lang);

						// set either retired or data, not both
						if (isRetired) {
							this.request.getInterface().kill(did, DB_GLOBAL_RECORD, tableschema.getName(), id, fname, sid, stamp, "Data");
							this.request.getInterface().kill(did, DB_GLOBAL_RECORD, tableschema.getName(), id, fname, sid, stamp, "Search");
							this.request.getInterface().kill(did, DB_GLOBAL_RECORD, tableschema.getName(), id, fname, sid, stamp, "Index");
							this.request.getInterface().kill(did, DB_GLOBAL_RECORD, tableschema.getName(), id, fname, sid, stamp, "Lang");
						} else if (dtype.isSearchable()) {
							this.request.getInterface().set(did, DB_GLOBAL_RECORD, tableschema.getName(), id, fname, sid, stamp, "Search", dtype.toSearch(value, lang));
							this.request.getInterface().set(did, DB_GLOBAL_RECORD, tableschema.getName(), id, fname, sid, stamp, "Index", newIdxValue);

							if (!isTenantLang)
								this.request.getInterface().set(did, DB_GLOBAL_RECORD, tableschema.getName(), id, fname, sid, stamp, "Lang", lang);
							else
								this.request.getInterface().kill(did, DB_GLOBAL_RECORD, tableschema.getName(), id, fname, sid, stamp, "Lang");
						} else {
							this.request.getInterface().kill(did, DB_GLOBAL_RECORD, tableschema.getName(), id, fname, sid, stamp, "Search");
							this.request.getInterface().kill(did, DB_GLOBAL_RECORD, tableschema.getName(), id, fname, sid, stamp, "Index");
							this.request.getInterface().kill(did, DB_GLOBAL_RECORD, tableschema.getName(), id, fname, sid, stamp, "Lang");
						}

						/* not good because prevents set from clean global
						boolean effectivelyEqual = ((oldIdxValue == null) && (newIdxValue == null)) || ((oldIdxValue != null) && oldIdxValue.equals(newIdxValue));

						if (! effectivelyEqual) {
						*/
							if ((oldIdxValue != null) && this.request.getInterface().isSet(did, DB_GLOBAL_INDEX_SUB, tableschema.getName(), fname, oldIdxValue, id, sid)) {
								// decrement index count for the old value
								// remove the old index value
								this.request.getInterface().dec(did, DB_GLOBAL_INDEX_SUB, tableschema.getName(), fname, oldIdxValue);
								this.request.getInterface().kill(did, DB_GLOBAL_INDEX_SUB, tableschema.getName(), fname, oldIdxValue, id, sid);
							}

							// don't bother with the indexes if not configured
							// or if there is a newer value for this field already set
							if (schema.isIndexed() && (newIdxValue != null)) {
								BigDateTime from = Struct.objectToBigDateTime(this.request.getInterface().get(did, DB_GLOBAL_RECORD, tableschema.getName(), id, fname, sid, stamp, "From"));
								BigDateTime to = Struct.objectToBigDateTime(this.request.getInterface().get(did, DB_GLOBAL_RECORD, tableschema.getName(), id, fname, sid, stamp, "To"));

								// increment index count
								// set the new index new
								String range = null;

								if (from != null)
									range = from.toString();

								if (to != null) {
									if (range == null)
										range = ":" + to.toString();
									else
										range += ":" + to.toString();
								}

								this.request.getInterface().inc(did, DB_GLOBAL_INDEX_SUB, tableschema.getName(), fname, newIdxValue);
								this.request.getInterface().set(did, DB_GLOBAL_INDEX_SUB, tableschema.getName(), fname, newIdxValue, id, sid, range);
							}
						//}
					}
				}
				
				subraw = this.request.getInterface().nextPeerKey( did, DB_GLOBAL_RECORD, tableschema.getName(), id, fname, sid);
			}
		}
		catch (Exception x) {
			Logger.error("Error indexing field: " + fname);
		}
	}

	/* TODO use TableView
	public void clearIndexField(DbTable table, String id) throws OperatingContextException {
		for (DbField field : table.fields.values()) {
			//if (! field.isIndexed())
			//	continue;

			// TODO if debug
			//Logger.info("   - Field: " + field.getName());

			this.clearIndexField(table, field, id);
		}
	}

	public void clearIndexField(DbTable tableschema, DbField schema, String id) throws OperatingContextException {
		String did = this.request.getTenant();

		if (schema == null) {
			Logger.error("Table not defined: " + tableschema.name);
			return;
		}

		SchemaResource schemares = ResourceHub.getResources().getSchema();
		DataType dtype = schemares.getType(schema.getTypeId());

		LocaleResource locale = OperationContext.getOrThrow().getTenant().getResources().getLocale();

		// --------------------------------------------------
		//   index updates herein may have race condition issues with the value counts
		//   this is ok as counts are just used for suggestions anyway
		// --------------------------------------------------

		String fname = schema.getName();

		try {
			// --------------------------------------
			// StaticScalar handling - Data or Retired (true) not both
			//
			//   fields([field name],"Data") = [value]
			//   fields([field name],"Lang") = [value]
			//   fields([field name],"Tags") = [value]
			//   fields([field name],"UpdateOnly") = [value]
			//   fields([field name],"Retired") = [value]
			// --------------------------------------
			if (! schema.isList() && ! schema.isDynamic()) {
				// find the first, newest, stamp
				byte[] stampraw = this.request.getInterface().nextPeerKey( did, DB_GLOBAL_RECORD, tableschema.name, id, fname, null);

				if (stampraw == null)
					return;

				BigDecimal stamp = Struct.objectToDecimal(ByteUtil.extractValue(stampraw));

				if (stamp == null)
					return;

				// try to get the data if any - note retired fields have no data
				boolean isRetired = this.request.getInterface().getAsBooleanOrFalse(did, DB_GLOBAL_RECORD, tableschema.name, id, fname, stamp, "Retired");

				if (isRetired)
					return;

				Object idxValue = this.request.getInterface().get(did, DB_GLOBAL_RECORD, tableschema.name, id, fname, stamp, "Index");

				if (idxValue == null) {
					Object value = this.request.getInterface().get(did, DB_GLOBAL_RECORD, tableschema.name, id, fname, stamp, "Data");

					if (value == null)
						return;

					String lang = locale.getDefaultLocale();

					if (this.request.getInterface().isSet(did, DB_GLOBAL_RECORD, tableschema.name, id, fname, stamp, "Lang"))
						lang = Struct.objectToString(this.request.getInterface().get(did, DB_GLOBAL_RECORD, tableschema.name, id, fname, stamp, "Lang"));

					idxValue = dtype.toIndex(value, lang);
				}

				if ((idxValue != null) && this.request.getInterface().isSet(did, DB_GLOBAL_INDEX, tableschema.name, fname, idxValue, id)) {
					// decrement index count for the old value
					// remove the old index value
					this.request.getInterface().dec(did, DB_GLOBAL_INDEX, tableschema.name, fname, idxValue);
					this.request.getInterface().kill(did, DB_GLOBAL_INDEX, tableschema.name, fname, idxValue, id);
				}

				return;
			}

			// --------------------------------------
			//
			// Handling for other types
			//
			// StaticList handling
			//   fields([field name],sid,"Data") = [value]
			//   fields([field name],sid,"Lang") = [value]
			//   fields([field name],sid,"Tags") = [value]			|value1|value2|etc...
			//   fields([field name],sid,"Retired") = [value]			|value1|value2|etc...
			//
			// DynamicScalar handling
			//   fields([field name],sid,"Data") = [value]
			//   fields([field name],sid,"Lang") = [value]
			//   fields([field name],sid,"From") = [value]			null means always was
			//   fields([field name],sid,"Tags") = [value]
			//   fields([field name],sid,"Retired") = [value]			|value1|value2|etc...
			//
			// DynamicList handling
			//   fields([field name],sid,"Data") = [value]
			//   fields([field name],sid,"Lang") = [value]
			//   fields([field name],sid,"From") = [value]			null means always was
			//   fields([field name],sid,"To") = [value]				null means always will be
			//   fields([field name],sid,"Tags") = [value]
			//   fields([field name],sid,"Retired") = [value]			|value1|value2|etc...
			// --------------------------------------
			// find the first, newest, stamp

			byte[] subraw = this.request.getInterface().nextPeerKey( did, DB_GLOBAL_RECORD, tableschema.name, id, fname, null);

			while (subraw != null) {
				String sid = Struct.objectToString(ByteUtil.extractValue(subraw));

				// error
				if (sid == null)
					return;

				// STAY IN LOOP until we run out of sub ids

				// find the first, newest, stamp
				byte[] stampraw = this.request.getInterface().nextPeerKey(did, DB_GLOBAL_RECORD, tableschema.name, id, fname, sid, null);

				if (stampraw != null) {
					BigDecimal stamp = Struct.objectToDecimal(ByteUtil.extractValue(stampraw));

					if (stamp != null) {
						// try to get the data if any - note retired fields have no data
						boolean isRetired = this.request.getInterface().getAsBooleanOrFalse(did, DB_GLOBAL_RECORD, tableschema.name, id, fname, sid, stamp, "Retired");

						if (! isRetired) {
							Object idxValue = this.request.getInterface().get(did, DB_GLOBAL_RECORD, tableschema.name, id, fname, sid, stamp, "Index");

							if (idxValue == null) {
								Object value = this.request.getInterface().get(did, DB_GLOBAL_RECORD, tableschema.name, id, fname, sid, stamp, "Data");

								if (value != null) {
									String lang = locale.getDefaultLocale();

									if (this.request.getInterface().isSet(did, DB_GLOBAL_RECORD, tableschema.name, id, fname, sid, stamp, "Lang"))
										lang = Struct.objectToString(this.request.getInterface().get(did, DB_GLOBAL_RECORD, tableschema.name, id, fname, sid, stamp, "Lang"));

									idxValue = dtype.toIndex(value, lang);
								}
							}

							if ((idxValue != null) && this.request.getInterface().isSet(did, DB_GLOBAL_INDEX_SUB, tableschema.name, fname, idxValue, id, sid)) {
								// decrement index count for the old value
								// remove the old index value
								this.request.getInterface().dec(did, DB_GLOBAL_INDEX_SUB, tableschema.name, fname, idxValue);
								this.request.getInterface().kill(did, DB_GLOBAL_INDEX_SUB, tableschema.name, fname, idxValue, id, sid);
							}
						}
					}
				}

				subraw = this.request.getInterface().nextPeerKey( did, DB_GLOBAL_RECORD, tableschema.name, id, fname, sid);
			}
		}
		catch (Exception x) {
			Logger.error("Error indexing field: " + fname);
		}
	}
	*/

	public boolean setStaticScalar(String table, String id, String field, Object data) throws OperatingContextException {
		RecordStruct fields = new RecordStruct()
				.with(field, new RecordStruct()
						.with("Data", data)
				);
		
		return this.setFields(table, id, fields);
	}
	
	public boolean updateStaticScalar(String table, String id, String field, Object data) throws OperatingContextException {
		RecordStruct fields = new RecordStruct()
				.with(field, new RecordStruct()
						.with("Data", data)
						.with("UpdateOnly", true)
				);
		
		return this.setFields(table, id, fields);
	}
	
	public boolean retireStaticScalar(String table, String id, String field) throws OperatingContextException {
		RecordStruct fields = new RecordStruct()
				.with(field, new RecordStruct()
						.with("Retired", true)
						.with("UpdateOnly", true)
				);
		
		return this.setFields(table, id, fields);
	}
	
	public boolean setStaticList(String table, String id, String field, String subid, Object data) throws OperatingContextException {
		RecordStruct fields = new RecordStruct()
				.with(field, new RecordStruct()
						.with(subid, new RecordStruct().with("Data", data))
				);
		
		return this.setFields(table, id, fields);
	}
	
	public boolean updateStaticList(String table, String id, String field, String subid, Object data) throws OperatingContextException {
		RecordStruct fields = new RecordStruct()
				.with(field, new RecordStruct()
						.with(subid, new RecordStruct()
								.with("Data", data)
								.with("UpdateOnly", true)
						)
				);
		
		return this.setFields(table, id, fields);
	}
	
	public boolean retireStaticList(String table, String id, String field, String subid) throws OperatingContextException {
		RecordStruct fields = new RecordStruct()
				.with(field, new RecordStruct()
						.with(subid, new RecordStruct()
								.with("Retired", true)
								.with("UpdateOnly", true)
						)
				);
		
		return this.setFields(table, id, fields);
	}
	
	// from is ms since 1970
	public boolean setDynamicScalar(String table, String id, String field, String subid, BigDateTime from, Object data) throws OperatingContextException {
		RecordStruct fields = new RecordStruct()
				.with(field, new RecordStruct()
						.with(subid, new RecordStruct()
								.with("Data", data)
								.with("From", from)
						)
				);
		
		return this.setFields(table, id, fields);
	}
	
	// from and to are ms since 1970
	public boolean setDynamicList(String table, String id, String field, String subid, BigDateTime from, BigDateTime to, Object data) throws OperatingContextException {
		RecordStruct fields = new RecordStruct()
				.with(field, new RecordStruct()
						.with(subid, new RecordStruct()
								.with("Data", data)
								.with("From", from)
								.with("To", to)
						)
				);
		
		return this.setFields(table, id, fields);
	}
	
	/* 
	public void rebiuldIndex(String table) {
	 ;
	 ; don't run when Java connector is active
	indexTable(table) n field
	 f  s field=$o(^dcSchema($p(table,"#"),"Fields",field)) q:field=""  d indexField(table,field)
	 quit
	 ;
	 ; don't run when Java connector is active
	indexField(table,field) n val,id,stamp,sid,fschema
	 i (table'["#")&(Domain'="") s table=table_"#"_Domain     ; support table instances
	 ;
	 m fschema=^dcSchema($p(table,"#"),"Fields",field)
	 ;
	 quit:'fschema("Indexed") 
	 ;
	 i 'fschema("List")&'fschema("Dynamic") k ^dcIndex1(table,field)
	 e  k ^dcIndex2(table,field)
	 ;
	 f  s id=$o(^dcRecord(table,id)) q:id=""  d
	 . i 'fschema("List")&'fschema("Dynamic") d  q
	 . . s stamp=$o(^dcRecord(table,id,field,""),-1) q:stamp=""   
	 . . s val=^dcRecord(table,id,field,stamp,"Data",0)
	 . . s val=$$val2Ndx(val)    
	 . . ;
	 . . ; don't index null
	 . . i val="" q
	 . . ;
	 . . s ^dcIndex1(table,field,val,id)=1
	 . . s ^dcIndex1(table,field,val)=^dcIndex1(table,field,val)+1
	 . ;
	 . f  s sid=$o(^dcRecord(table,id,field,sid),-1) q:sid=""  d
	 . . s stamp=$o(^dcRecord(table,id,field,sid,""),-1) q:stamp="" 	
	 . . ;
	 . . s val=^dcRecord(table,id,field,sid,stamp,"Data",0)
	 . . s val=$$val2Ndx(val)    
	 . . ;
	 . . ; don't index null
	 . . i val="" q
	 . . ;
	 . . s ^dcIndex2(table,field,val,id,sid)=1
	 . . s ^dcIndex2(table,field,val)=^dcIndex2(table,field,val)+1
	 ;
	 quit
	 ;
	}	
	 */

	public boolean isPresent(String table, String id) throws OperatingContextException {
		try {
			if (this.request.getInterface().hasAny(this.request.getTenant(), DB_GLOBAL_RECORD, table, id))
				return true;
		}
		catch (DatabaseException x) {
			// TODO logger
		}

		return false;
	}

	public boolean isRetired(String table, String id) throws OperatingContextException {
		try {
			if (! this.request.getInterface().hasAny(this.request.getTenant(), DB_GLOBAL_RECORD, table, id))
				return true;

			if (Struct.objectToBooleanOrFalse(this.getStaticScalar(table, id, "Retired")))
				return true;
		} 
		catch (DatabaseException x) {
			// TODO logger
		}
		
		return false;
	}
	
	/*
	 ; check not only retired, but if this record was active during the period of time
	 ; indicated by "when".  If a record has no From then it is considered to be 
	 ; active indefinitely in the past, prior to To.  If there is no To then record
	 ; is active current and since From.
	 */
	public boolean isCurrent(String table, String id) throws OperatingContextException {
		if (this.isRetired(table, id))
			return false;
		
		if (! this.historical) {
			BigDateTime to = Struct.objectToBigDateTime(this.getStaticScalar(table, id, "To"));
			
			// when must come before to
			if ((to != null) && (this.when.compareTo(to) != -1))
				return false;
		}
		
		BigDateTime from = Struct.objectToBigDateTime(this.getStaticScalar(table, id, "From"));
		
		// when must come after - or at - from
		if ((from != null) && (this.when.compareTo(from) >= 0))
			return false;
		
		return true;
	}
	
	public Object getStaticScalar(String table, String id, String field) throws OperatingContextException {
		return this.getStaticScalar(table, id, field, null);
	}
	
	public Object getStaticScalar(String table, String id, String field, String format) throws OperatingContextException {
		byte[] val = this.getStaticScalarRaw(table, id, field);

		return TableUtil.normalizeFormatRaw(table, id, field, val, format);
	}

	public byte[] getStaticScalarRaw(String table, String id, String field) throws OperatingContextException {
		return this.getStaticScalarRaw(table, id, field, "Data");
	}

	public byte[] getStaticScalarRaw(String table, String id, String field, String area) throws OperatingContextException {
		// checks the Retired flag 
		BigDecimal stamp = this.getStaticScalarStamp(table, id, field);
		
		if (stamp == null)
			return null;
		
		try {
			return this.request.getInterface().getRaw(this.request.getTenant(), DB_GLOBAL_RECORD, table, id, field, stamp, area);
		}
		catch (DatabaseException x) {
			Logger.error("getStaticScalar error: " + x);
		}
		
		return null;
	}

	public Object getStaticScalar(String table, String id, String field, BigDecimal stamp, String format) throws OperatingContextException {
		byte[] val = this.getStaticScalarRaw(table, id, field, stamp);

		return TableUtil.normalizeFormatRaw(table, id, field, val, format);
	}

	public byte[] getStaticScalarRaw(String table, String id, String field, BigDecimal stamp) throws OperatingContextException {
		return this.getStaticScalarRaw(table, id, field, stamp, "Data");
	}

	public byte[] getStaticScalarRaw(String table, String id, String field, BigDecimal stamp, String area) throws OperatingContextException {
		if (stamp == null)
			return null;

		try {
			return this.request.getInterface().getRaw(this.request.getTenant(), DB_GLOBAL_RECORD, table, id, field, stamp, area);
		}
		catch (DatabaseException x) {
			Logger.error("getStaticScalar error: " + x);
		}

		return null;
	}

	public RecordStruct getStaticScalarExtended(String table, String id, String field, String format) throws OperatingContextException {
		BigDecimal stamp = this.getStaticScalarStamp(table, id, field);
		
		if (stamp == null)
			return null;
		
		try {
			return RecordStruct.record()
				.with("Data", this.getStaticScalar(table, id, field, format))
				.with("Tags", this.request.getInterface().get(this.request.getTenant(), DB_GLOBAL_RECORD, table, id, field, stamp, "Tags"))
				.with("Retired", this.request.getInterface().get(this.request.getTenant(), DB_GLOBAL_RECORD, table, id, field, stamp, "Retired"));
		}
		catch (DatabaseException x) {
			Logger.error("getStaticScalar error: " + x);
		}
		
		return null;
	}
	
	public BigDecimal getStaticScalarStamp(String table, String id, String field) throws OperatingContextException {
		try {
			byte[] olderStamp = this.request.getInterface().getOrNextPeerKey(this.request.getTenant(), DB_GLOBAL_RECORD, table, id, field, this.request.getStamp());
			
			if (olderStamp == null) 
				return null;
			
			BigDecimal oldStamp = Struct.objectToDecimal(ByteUtil.extractValue(olderStamp));
			
			if (this.request.getInterface().getAsBooleanOrFalse(this.request.getTenant(), DB_GLOBAL_RECORD, table, id, field, oldStamp, "Retired"))
				return null;
			
			return oldStamp;
		}
		catch (DatabaseException x) {
			Logger.error("getStaticScalar error: " + x);
		}
		
		return null;
	}
	
	public Object getStaticList(String table, String id, String field, String subid) throws OperatingContextException {
		return this.getStaticList(table, id, field, subid, null);
	}
	
	public Object getStaticList(String table, String id, String field, String subid, String format) throws OperatingContextException {
		byte[] val = this.getStaticListRaw(table, id, field, subid);
		
		return TableUtil.normalizeFormatRaw(table, id, field, val, format);
	}
	
	public byte[] getStaticListRaw(String table, String id, String field, String subid) throws OperatingContextException {
		return this.getStaticListRaw(table, id, field, subid, "Data");
	}
	
	public byte[] getStaticListRaw(String table, String id, String field, String subid, String area) throws OperatingContextException {
		// checks the Retired flag 
		BigDecimal stamp = this.getListStamp(table, id, field, subid);
		
		if (stamp == null)
			return null;
		
		try {
			return this.request.getInterface().getRaw(this.request.getTenant(), DB_GLOBAL_RECORD, table, id, field, subid, stamp, area);
		}
		catch (DatabaseException x) {
			Logger.error("getStaticList error: " + x);
		}
		
		return null;
	}

	public Object getStaticList(String table, String id, String field, String subid, BigDecimal stamp, String format) throws OperatingContextException {
		byte[] val = this.getStaticListRaw(table, id, field, subid, stamp);

		return TableUtil.normalizeFormatRaw(table, id, field, val, format);
	}

	public byte[] getStaticListRaw(String table, String id, String field, String subid, BigDecimal stamp) throws OperatingContextException {
		return this.getStaticListRaw(table, id, field, subid, stamp, "Data");
	}

	public byte[] getStaticListRaw(String table, String id, String field, String subid, BigDecimal stamp, String area) throws OperatingContextException {
		if (stamp == null)
			return null;

		try {
			return this.request.getInterface().getRaw(this.request.getTenant(), DB_GLOBAL_RECORD, table, id, field, subid, stamp, area);
		}
		catch (DatabaseException x) {
			Logger.error("getStaticList error: " + x);
		}

		return null;
	}

	public RecordStruct getStaticListExtended(String table, String id, String field, String subid, String format) throws OperatingContextException {
		BigDecimal stamp = this.getListStamp(table, id, field, subid);
		
		if (stamp == null)
			return null;
		
		try {
			return RecordStruct.record()
				.with("SubId", subid)
				.with("Data", this.getStaticList(table, id, field, subid, format))
				.with("Tags", this.request.getInterface().get(this.request.getTenant(), DB_GLOBAL_RECORD, table, id, field, subid, stamp, "Tags"))
				.with("Retired", this.request.getInterface().get(this.request.getTenant(), DB_GLOBAL_RECORD, table, id, field, subid, stamp, "Retired"));
		}
		catch (DatabaseException x) {
			Logger.error("getStaticList error: " + x);
		}
		
		return null;
	}
	
	public List<String> getStaticListKeys(String table, String id, String field) throws OperatingContextException {
		List<String> ret = new ArrayList<>();
		
		try {
			byte[] subid = this.request.getInterface().nextPeerKey(this.request.getTenant(), DB_GLOBAL_RECORD, table, id, field, null);
			
			while (subid != null) {
				Object sid = ByteUtil.extractValue(subid);

				// checks retired
				BigDecimal stamp = this.getListStamp(table, id, field, (String) sid);

				if (stamp != null)
					ret.add(Struct.objectToString(sid));

				subid = this.request.getInterface().nextPeerKey(this.request.getTenant(), DB_GLOBAL_RECORD, table, id, field, sid);
			}
		}
		catch (DatabaseException x) {
			Logger.error("getStaticList error: " + x);
		}
		
		return ret;
	}
	
	public Object getDynamicScalar(String table, String id, String field) throws OperatingContextException {
		return this.getDynamicScalar(table, id, field, null);
	}
	
	public Object getDynamicScalar(String table, String id, String field, String format) throws OperatingContextException {
		byte[] val = this.getDynamicScalarRaw(table, id, field);
		
		return TableUtil.normalizeFormatRaw(table, id, field, val, format);
	}
	
	public byte[] getDynamicScalarRaw(String table, String id, String field) throws OperatingContextException {
		return this.getDynamicScalarRaw(table, id, field, "Data");
	}
	
	public byte[] getDynamicScalarRaw(String table, String id, String field, String area) throws OperatingContextException {
		String subid = this.getDynamicScalarSubId(table, id, field);
		
		if (StringUtil.isEmpty(subid))
			return null;
		
		// checks the Retired flag 
		BigDecimal stamp = this.getListStamp(table, id, field, subid);
		
		if (stamp == null)
			return null;
		
		try {
			return this.request.getInterface().getRaw(this.request.getTenant(), DB_GLOBAL_RECORD, table, id, field, subid, stamp, area);
		}
		catch (DatabaseException x) {
			Logger.error("getDynamicScalar error: " + x);
		}
		
		return null;
	}
	
	public RecordStruct getDynamicScalarExtended(String table, String id, String field, String format) throws OperatingContextException {
		String subid = this.getDynamicScalarSubId(table, id, field);
		
		if (StringUtil.isEmpty(subid))
			return null;
		
		BigDecimal stamp = this.getListStamp(table, id, field, subid);
		
		if (stamp == null)
			return null;
		
		try {
			return RecordStruct.record()
				.with("SubId", subid)
				.with("Data", this.request.getInterface().get(this.request.getTenant(), DB_GLOBAL_RECORD, table, id, field, subid, stamp, "Data"))
				.with("Tags", this.request.getInterface().get(this.request.getTenant(), DB_GLOBAL_RECORD, table, id, field, subid, stamp, "Tags"))
				.with("Retired", this.request.getInterface().get(this.request.getTenant(), DB_GLOBAL_RECORD, table, id, field, subid, stamp, "Retired"))
				.with("From", this.request.getInterface().get(this.request.getTenant(), DB_GLOBAL_RECORD, table, id, field, subid, stamp, "From"));
		}
		catch (DatabaseException x) {
			Logger.error("getDynamicScalar error: " + x);
		}
		
		return null;
	}
	
	public String getDynamicScalarSubId(String table, String id, String field) throws OperatingContextException {
		if (! this.historical) {
			BigDateTime to = Struct.objectToBigDateTime(this.getStaticScalar(table, id, "To"));
			
			// when must come before to
			if ((to != null) && (this.when.compareTo(to) != -1))
				return null;
		}
		
		BigDateTime matchWhen = null;
		String matchSid = null;
		
		try {
			byte[] subid = this.request.getInterface().nextPeerKey(this.request.getTenant(), DB_GLOBAL_RECORD, table, id, field, null);
			
			while (subid != null) {
				Object sid = ByteUtil.extractValue(subid);
				
				byte[] stmp = this.request.getInterface().getOrNextPeerKey(this.request.getTenant(), DB_GLOBAL_RECORD, table, id, field, sid, this.request.getStamp());
				
				if (stmp != null) {
					Object stamp = ByteUtil.extractValue(stmp);
					
					if (!this.request.getInterface().getAsBooleanOrFalse(this.request.getTenant(), DB_GLOBAL_RECORD, table, id, field, sid, stamp, "Retired")) {
						BigDateTime from = this.request.getInterface().getAsBigDateTime(this.request.getTenant(), DB_GLOBAL_RECORD,  table, id, field, sid, stamp, "From");
						
						if (from == null) 
							from = Struct.objectToBigDateTime(this.getStaticScalar(table, id, "From"));
						
						if ((from == null) && (matchWhen == null))
							matchSid = Struct.objectToString(sid);
						
						// if `from` is before or at `when` and if `from` is greater than a previous match 
						else if ((from != null) && (from.compareTo(when) <= 0)) {
							if ((matchWhen == null) || (from.compareTo(matchWhen) > 0)) {
								matchWhen = from;
								matchSid = Struct.objectToString(sid);
							}
						}
					}					
				}
				
				subid = this.request.getInterface().nextPeerKey(this.request.getTenant(), DB_GLOBAL_RECORD, table, id, field, sid);
			}
		}
		catch (DatabaseException x) {
			Logger.error("getDynamicScalar error: " + x);
		}
		
		return matchSid;
	}
	
	public List<String> getDynamicScalarKeys(String table, String id, String field) throws OperatingContextException {
		List<String> ret = new ArrayList<>();
		
		try {
			byte[] subid = this.request.getInterface().nextPeerKey(this.request.getTenant(), DB_GLOBAL_RECORD, table, id, field, null);
			
			while (subid != null) {
				Object sid = ByteUtil.extractValue(subid);
				
				ret.add(Struct.objectToString(sid));
				
				subid = this.request.getInterface().nextPeerKey(this.request.getTenant(), DB_GLOBAL_RECORD, table, id, field, sid);
			}
		}
		catch (DatabaseException x) {
			Logger.error("getDynamicScalar error: " + x);
		}
		
		return ret;
	}
	
	public Object getDynamicList(String table, String id, String field, String subid) throws OperatingContextException {
		return this.getDynamicList(table, id, field, subid, null);
	}
	
	public Object getDynamicList(String table, String id, String field, String subid, String format) throws OperatingContextException {
		byte[] val = this.getDynamicListRaw(table, id, field, subid);
		
		return TableUtil.normalizeFormatRaw(table, id, field, val, format);
	}
	
	public byte[] getDynamicListRaw(String table, String id, String field, String subid) throws OperatingContextException {
		return this.getDynamicListRaw(table, id, field, subid, "Data");
	}
	
	public byte[] getDynamicListRaw(String table, String id, String field, String subid, String area) throws OperatingContextException {
		// checks the Retired flag 
		BigDecimal stamp = this.getListStamp(table, id, field, subid);
		
		if (stamp == null)
			return null;
		
		try {
			return this.request.getInterface().getRaw(this.request.getTenant(), DB_GLOBAL_RECORD, table, id, field, subid, stamp, area);
		}
		catch (DatabaseException x) {
			Logger.error("getDynamicList error: " + x);
		}
		
		return null;
	}
	
	public RecordStruct getDynamicListExtended(String table, String id, String field, String subid, String format) throws OperatingContextException {
		BigDecimal stamp = this.getListStamp(table, id, field, subid);
		
		if (stamp == null)
			return null;
		
		try {
			return RecordStruct.record()
				.with("SubId", subid)
				.with("Data", this.request.getInterface().get(this.request.getTenant(), DB_GLOBAL_RECORD, table, id, field, subid, stamp, "Data"))
				.with("Tags", this.request.getInterface().get(this.request.getTenant(), DB_GLOBAL_RECORD, table, id, field, subid, stamp, "Tags"))
				.with("Retired", this.request.getInterface().get(this.request.getTenant(), DB_GLOBAL_RECORD, table, id, field, subid, stamp, "Retired"))
				.with("From", this.request.getInterface().get(this.request.getTenant(), DB_GLOBAL_RECORD, table, id, field, subid, stamp, "From"))
				.with("To", this.request.getInterface().get(this.request.getTenant(), DB_GLOBAL_RECORD, table, id, field, subid, stamp, "To"));
		}
		catch (DatabaseException x) {
			Logger.error("getDynamicList error: " + x);
		}
		
		return null;
	}
		
	public BigDecimal getListStamp(String table, String id, String field, String subid) throws OperatingContextException {
		try {
			byte[] olderStamp = this.request.getInterface().getOrNextPeerKey(this.request.getTenant(), DB_GLOBAL_RECORD, table, id, field, subid, this.request.getStamp());
			
			if (olderStamp == null) 
				return null;
			
			BigDecimal oldStamp = Struct.objectToDecimal(ByteUtil.extractValue(olderStamp));
			
			if (this.request.getInterface().getAsBooleanOrFalse(this.request.getTenant(), DB_GLOBAL_RECORD, table, id, field, subid, oldStamp, "Retired"))
				return null;
			
			BigDateTime to = this.request.getInterface().getAsBigDateTime(this.request.getTenant(), DB_GLOBAL_RECORD, table, id, field, subid, oldStamp, "To");
			
			if (to == null) 
				to = Struct.objectToBigDateTime(this.getStaticScalar(table, id, "To"));
			
			// if `to` is before or at `when` then bad
			if ((to != null) && (to.compareTo(this.when) <= 0))
				return null;
			
			BigDateTime from = this.request.getInterface().getAsBigDateTime(this.request.getTenant(), DB_GLOBAL_RECORD, table, id, field, subid, oldStamp, "From");
			
			if (from == null) 
				from = Struct.objectToBigDateTime(this.getStaticScalar(table, id, "From"));
			
			if (from == null) 
				return oldStamp;
			
			// if `from` is before or at `when` then good
			if (from.compareTo(this.when) <= 0)
				return oldStamp;
		}
		catch (DatabaseException x) {
			Logger.error("getDynamicList error: " + x);
		}
		
		return null;
	}

	public List<BigDecimal> getListStamps(String table, String id, String field, String subid) throws OperatingContextException {
		List<BigDecimal> ret = new ArrayList<>();

		try {
			byte[] stamp = this.request.getInterface().nextPeerKey(this.request.getTenant(), DB_GLOBAL_RECORD, table, id, field, subid, null);

			while (stamp != null) {
				Object sid = ByteUtil.extractValue(stamp);

				ret.add(Struct.objectToDecimal(sid));

				stamp = this.request.getInterface().nextPeerKey(this.request.getTenant(), DB_GLOBAL_RECORD, table, id, field, subid, sid);
			}
		}
		catch (DatabaseException x) {
			Logger.error("getDynamicList error: " + x);
		}


		return ret;
	}

	public List<String> getDynamicListKeys(String table, String id, String field) throws OperatingContextException {
		List<String> ret = new ArrayList<>();
		
		try {
			byte[] subid = this.request.getInterface().nextPeerKey(this.request.getTenant(), DB_GLOBAL_RECORD, table, id, field, null);
			
			while (subid != null) {
				Object sid = ByteUtil.extractValue(subid);
				
				ret.add(Struct.objectToString(sid));
				
				subid = this.request.getInterface().nextPeerKey(this.request.getTenant(), DB_GLOBAL_RECORD, table, id, field, sid);
			}
		}
		catch (DatabaseException x) {
			Logger.error("getDynamicListKeys error: " + x);
		}
		
		return ret;
	}
	
	// TODO review these three methods, not sure they provide useful functions
	public List<Object> get(String table, String id, String field) throws OperatingContextException {
		return this.get(table, id, field, null, null);
	}
	
	public List<Object> get(String table, String id, String field, String subid, String format) throws OperatingContextException {
		List<Object> ret = new ArrayList<>();
		
		SchemaResource sch = ResourceHub.getResources().getSchema();
		DbField schema = sch.getDbField(table, field);
		
		if (schema == null)
			return ret;
		
		if (!schema.isList() && !schema.isDynamic()) {
			ret.add(this.getStaticScalar(table, id, field, format));
			return ret;
		}
		
		if (!schema.isList() && schema.isDynamic()) {
			ret.add(this.getDynamicScalar(table, id, field, format));
			return ret;
		}
		
		if (subid != null) {
			if (schema.isList() && !schema.isDynamic())
				ret.add(this.getStaticList(table, id, field, subid));
			else
				ret.add(this.getDynamicList(table, id, field, subid));	// TODO check if this returns null sometimes, not what we want right?
		}
		else {
			try {
				byte[] bsubid = this.request.getInterface().nextPeerKey(this.request.getTenant(), DB_GLOBAL_RECORD, table, id, field, null);
				
				while (bsubid != null) {
					Object sid = ByteUtil.extractValue(bsubid);
					
					if (schema.isList() && !schema.isDynamic())
						ret.add(this.getStaticList(table, id, field, Struct.objectToString(sid), format));
					else
						ret.add(this.getDynamicList(table, id, field, Struct.objectToString(sid), format));
					
					bsubid = this.request.getInterface().nextPeerKey(this.request.getTenant(), DB_GLOBAL_RECORD, table, id, field, sid);
				}
			}
			catch (DatabaseException x) {
				Logger.error("getDynamicList error: " + x);
			}
		}
		
		return ret;
	}
	
	public List<Object> getExtended(String table, String id, String field, String subid, String format) throws OperatingContextException {
		List<Object> ret = new ArrayList<>();
		
		SchemaResource sch = ResourceHub.getResources().getSchema();
		DbField schema = sch.getDbField(table, field);
		
		if (schema == null)
			return ret;
		
		if (!schema.isList() && !schema.isDynamic()) {
			ret.add(this.getStaticScalarExtended(table, id, field, format));
			return ret;
		}
		
		if (!schema.isList() && schema.isDynamic()) {
			ret.add(this.getDynamicScalarExtended(table, id, field, format));
			return ret;
		}
		
		if (subid != null) {
			if (schema.isList() && !schema.isDynamic())
				ret.add(this.getStaticListExtended(table, id, field, subid, format));
			else
				ret.add(this.getDynamicListExtended(table, id, field, subid, format));	// TODO check if this returns null sometimes, not what we want right?
		}
		else {
			try {
				byte[] bsubid = this.request.getInterface().nextPeerKey(this.request.getTenant(), DB_GLOBAL_RECORD, table, id, field, null);
				
				while (bsubid != null) {
					Object sid = ByteUtil.extractValue(bsubid);
					
					if (schema.isList() && !schema.isDynamic())
						ret.add(this.getStaticListExtended(table, id, field, Struct.objectToString(sid), format));
					else
						ret.add(this.getDynamicListExtended(table, id, field, Struct.objectToString(sid), format));
					
					bsubid = this.request.getInterface().nextPeerKey(this.request.getTenant(), DB_GLOBAL_RECORD, table, id, field, sid);
				}
			}
			catch (DatabaseException x) {
				Logger.error("getDynamicList error: " + x);
			}
		}
		
		return ret;
	}
	
	// subid null for all
	public List<byte[]> getRaw(String table, String id, String field, String subid, String area) throws OperatingContextException {
		List<byte[]> ret = new ArrayList<>();
		
		SchemaResource schema = ResourceHub.getResources().getSchema();
		DbField schemafld = schema.getDbField(table, field);
		
		if (schemafld == null)
			return ret;
		
		DataType dtype = schema.getType(schemafld.getTypeId());
		
		if (dtype == null)
			return ret;
		
		if ("Index".equals(area) && ! dtype.isSearchable())
			area = "Data";
		else if ("Search".equals(area) && ! dtype.isSearchable())
			area = "Data";
		
		if (! schemafld.isList() && !schemafld.isDynamic()) {
			ret.add(this.getStaticScalarRaw(table, id, field, area));
			return ret;
		}
		
		if (! schemafld.isList() && schemafld.isDynamic()) {
			ret.add(this.getDynamicScalarRaw(table, id, field, area));
			return ret;
		}
		
		if (subid != null) {
			if (schemafld.isList() && ! schemafld.isDynamic())
				ret.add(this.getStaticListRaw(table, id, field, subid, area));
			else
				ret.add(this.getDynamicListRaw(table, id, field, subid, area));	// TODO check if this returns null sometimes, not what we want right?
		}
		else {
			try {
				byte[] bsubid = this.request.getInterface().nextPeerKey(this.request.getTenant(), DB_GLOBAL_RECORD, table, id, field, null);
				
				while (bsubid != null) {
					Object sid = ByteUtil.extractValue(bsubid);
					
					if (schemafld.isList() && ! schemafld.isDynamic())
						ret.add(this.getStaticListRaw(table, id, field, Struct.objectToString(sid), area));
					else
						ret.add(this.getDynamicListRaw(table, id, field, Struct.objectToString(sid), area));	// TODO check if this returns null sometimes, not what we want right?
					
					bsubid = this.request.getInterface().nextPeerKey(this.request.getTenant(), DB_GLOBAL_RECORD, table, id, field, sid);
				}
			}
			catch (DatabaseException x) {
				Logger.error("getDynamicList error: " + x);
			}
		}
		
		return ret;
	}

	// retire means disable
	public void retireRecord(String table, String id) throws OperatingContextException {
		if (! this.isRetired(table, id)) {
			this.setStaticScalar(table, id, "Retired", true);
			
			this.executeTrigger(table, id,"AfterRetire", null);
		}
	}

	public void reviveRecord(String table, String id) throws OperatingContextException {
		if (this.isRetired(table, id)) {
			this.setStaticScalar(table, id, "Retired", false);
			
			this.executeTrigger(table, id, "AfterRevive",null);
		}
	}

	/* TODO
	public void deleteRecord(String table, String id) throws OperatingContextException {
		if (! this.isRetired(table, id)) {

			// TODO add db tigger option
			//d runFilter("Retire") quit:Errors  ; if any violations in filter then do not proceed

			SchemaResource schema = ResourceHub.getResources().getSchema();
			DbTable schemaTbl = schema.getDbTable(table);

			// needs to support clearing ForeignKey fields as well
			this.clearIndexField(schemaTbl, id);

			try {
				this.request.getInterface().kill(this.request.getTenant(), DB_GLOBAL_RECORD, table, id);
			}
			catch (DatabaseException x) {
				Logger.error("remove record error: " + x);
			}
		}
	}
	*/

	/* TODO something like this...
	 ; check to see if value is current with the given when+historical settings
	has(value,table,id,field,when,format,historical) i (table="")!(id="")!(field="") quit 0
	 i (table'["#")&(Domain'="") s table=table_"#"_Domain     ; support table instances
	 ;
	 n fschema m fschema=^dcSchema($p(table,"#"),"Fields",field)
	 i 'fschema("List")&'fschema("Dynamic") quit ($$get1(table,id,field,format)=value)
	 i 'fschema("List")&fschema("Dynamic") quit ($$get3(table,id,field,when,format,historical)=value)
	 ;
	 n sid,fnd
	 f  s sid=$o(^dcRecord(table,id,field,sid)) q:sid=""  d  q:fnd
	 . s:($$get4(table,id,field,sid,when,format)=value) fnd=1
	 ;
	 quit fnd
	 ;
*/
	
	public boolean checkSelect(RecordScope scope, String table, String id, RecordStruct where) throws OperatingContextException {
		if (where == null)
			return true;
		
		IExpression expression = (IExpression) where.getFieldAsAny("_Expression");
		
		if (expression == null)
			expression = ExpressionUtil.initExpression(table, where);
		
		if (expression == null) {
			Logger.error("bad expression");
			return false;
		}
	
		return expression.check(this, scope, table, id).accepted;
	}
	
	public void traverseSubIds(String table, String id, String fname, Function<Object,Boolean> out) throws OperatingContextException {
		String did = this.request.getTenant();
		
		try {
			byte[] subid = this.request.getInterface().nextPeerKey(did, DB_GLOBAL_RECORD, table, id, fname, null);
			
			while (subid != null) {
				Object sid = ByteUtil.extractValue(subid);
				
				// if stamp is null it means Retired
				if (this.getListStamp(table, id, fname, sid.toString()) != null)
					out.apply(sid);
				
				subid = this.request.getInterface().nextPeerKey(did, DB_GLOBAL_RECORD, table, id, fname, sid);
			}
		}
		catch (Exception x) {
			Logger.error("getDynamicList error: " + x);
		}
	}

	public void traverseRecords(IVariableAware scope, String table, IFilter out) throws OperatingContextException {
		String did = this.request.getTenant();
		
		try {
			byte[] id = this.request.getInterface().nextPeerKey(did, DB_GLOBAL_RECORD, table, null);
			
			while (id != null) {
				Object oid = ByteUtil.extractValue(id);

				if (! out.check(this, scope, table, oid).resume)
					return;

				id = this.request.getInterface().nextPeerKey(did, DB_GLOBAL_RECORD, table, oid);
			}
		}
		catch (Exception x) {
			Logger.error("traverseRecords error: " + x);
		}
	}
	
	public IFilter traverseIndex(IVariableAware scope, String table, String fname, Object val, IFilter out) throws OperatingContextException {
		return this.traverseIndex(scope, table, fname, val, null, out);
	}
	
	public IFilter traverseIndex(IVariableAware scope, String table, String fname, Object val, String subid, IFilter out) throws OperatingContextException {
		String did = this.request.getTenant();
		
		SchemaResource schema = ResourceHub.getResources().getSchema();
		DbField ffdef = schema.getDbField(table, fname);
		
		if (ffdef == null)
			return out;

		DataType dtype = schema.getType(ffdef.getTypeId());

		if (dtype == null)
			return out;

		val = dtype.toIndex(val, "eng");	// TODO increase locale support

		try {
			byte[] recid = request.getInterface().nextPeerKey(did, ffdef.getIndexName(), table, fname, val, null);
			
			while (recid != null) {
				Object rid = ByteUtil.extractValue(recid);

				if (ffdef.isStaticScalar()) {
					ExpressionResult filterResult = out.check(this, scope, table, rid);

					if (! filterResult.resume)
						return out;
				}
				else {
					byte[] recsid = request.getInterface().nextPeerKey(did, DB_GLOBAL_INDEX_SUB, table, fname, val, rid, null);

					while (recsid != null) {
						Object rsid = ByteUtil.extractValue(recsid);

						if ((subid == null) || subid.equals(rsid)) {
							String range = request.getInterface().getAsString(did, DB_GLOBAL_INDEX_SUB, table, fname, val, rid, rsid);

							if (StringUtil.isEmpty(range) || (when == null)) {
								ExpressionResult filterResult = out.check(this, scope, table, rid);

								if (! filterResult.resume)
									return out;
							}
							else {
								int pos = range.indexOf(':');

								BigDateTime from = null;
								BigDateTime to = null;

								if (pos == -1) {
									from = BigDateTime.parse(range);
								}
								else if (pos == 0) {
									to = BigDateTime.parse(range.substring(1));
								}
								else {
									from = BigDateTime.parse(range.substring(0, pos));
									to = BigDateTime.parse(range.substring(pos + 1));
								}

								if (((from == null) || (when.compareTo(from) >= 0)) && ((to == null) || (when.compareTo(to) < 0))) {
									ExpressionResult filterResult = out.check(this, scope, table, rid);

									if (! filterResult.resume)
										return out;
								}
							}
						}

						recsid = request.getInterface().nextPeerKey(did, DB_GLOBAL_INDEX_SUB, table, fname, val, rid, rsid);
					}
				}

				recid = request.getInterface().nextPeerKey(did, ffdef.getIndexName(), table, fname, val, rid);
			}
		}
		catch (Exception x) {
			Logger.error("traverseIndex error: " + x);
		}
		
		return out;
	}
	
	public Object firstInIndex(String table, String fname, Object val, boolean checkcurrent) throws OperatingContextException {
		String did = this.request.getTenant();
		
		SchemaResource schema = ResourceHub.getResources().getSchema();
		DbField ffdef = schema.getDbField(table, fname);
		
		if (ffdef == null)
			return null;

		DataType dtype = schema.getType(ffdef.getTypeId());

		if (dtype == null)
			return null;

		val = dtype.toIndex(val, "eng");	// TODO increase locale support

		try {
			byte[] recid = request.getInterface().nextPeerKey(did, ffdef.getIndexName(), table, fname, val, null);
			
			while (recid != null) {
				Object rid = ByteUtil.extractValue(recid);
				
				if (! checkcurrent || this.isCurrent(table, rid.toString())) {
	
					if (ffdef.isStaticScalar()) 
						return rid;

					byte[] recsid = request.getInterface().nextPeerKey(did, DB_GLOBAL_INDEX_SUB, table, fname, val, rid, null);
					
					while (recsid != null) {
						Object rsid = ByteUtil.extractValue(recsid);
						
						String range = request.getInterface().getAsString(did, DB_GLOBAL_INDEX_SUB, table, fname, val, rid, rsid);
						
						if (StringUtil.isEmpty(range) || (when == null))
							return rid;
						
						int pos = range.indexOf(':');
						
						BigDateTime from = null;
						BigDateTime to = null;
						
						if (pos == -1) {
							from = BigDateTime.parse(range);
						}
						else if (pos == 0) {
							to = BigDateTime.parse(range.substring(1));
						}
						else {
							from = BigDateTime.parse(range.substring(0, pos));
							to = BigDateTime.parse(range.substring(pos + 1));
						}
						
						if (((from == null) || (this.when.compareTo(from) >= 0)) && ((to == null) || (this.when.compareTo(to) < 0)))
							return rid;
						
						recsid = request.getInterface().nextPeerKey(did, DB_GLOBAL_INDEX_SUB, table, fname, val, rsid);
					}
				}
				
				recid = request.getInterface().nextPeerKey(did, ffdef.getIndexName(), table, fname, val, rid);
			}
		}
		catch (Exception x) {
			Logger.error("traverseIndex error: " + x);
		}
		
		return null;
	}	
	
	// traverse the values
	public void traverseIndexValRange(IVariableAware scope, String table, String fname, Object fromval, Object toval, IFilter out) throws OperatingContextException {
		String did = this.request.getTenant();
		
		SchemaResource schema = ResourceHub.getResources().getSchema();
		DbField ffdef = schema.getDbField(table, fname);
		
		if (ffdef == null)
			return;

		DataType dtype = schema.getType(ffdef.getTypeId());

		if (dtype == null)
			return;

		fromval = dtype.toIndex(fromval, "eng");	// TODO increase locale support

		toval = dtype.toIndex(toval, "eng");	// TODO increase locale support

		try {
			byte[] valb = request.getInterface().getOrNextPeerKey(did, ffdef.getIndexName(), table, fname, fromval);
			byte[] valfin = (toval != null) ? ByteUtil.buildKey(toval) : null;
			
			while (valb != null) {
				// check if past "To"
				if ((valfin != null) && (ByteUtil.compareKeys(valb, valfin) >= 0))
					break;
				
				Object val = ByteUtil.extractValue(valb);
				
				ExpressionResult filterResult = out.check(this, scope, table, val);
				
				if (! filterResult.resume)
					return;
				
				valb = request.getInterface().nextPeerKey(did, ffdef.getIndexName(), table, fname, val);
			}
		}
		catch (Exception x) {
			Logger.error("traverseIndex error: " + x);
		}
	}	
	
	// traverse the record ids
	public void traverseIndexRange(IVariableAware scope, String table, String fname, Object fromval, Object toval, IFilter out) throws OperatingContextException {
		String did = this.request.getTenant();
		
		SchemaResource schema = ResourceHub.getResources().getSchema();
		DbField ffdef = schema.getDbField(table, fname);
		
		if (ffdef == null)
			return;

		DataType dtype = schema.getType(ffdef.getTypeId());

		if (dtype == null)
			return;

		fromval = dtype.toIndex(fromval, "eng");	// TODO increase locale support

		toval = dtype.toIndex(toval, "eng");	// TODO increase locale support

		try {
			byte[] valb = request.getInterface().getOrNextPeerKey(did, ffdef.getIndexName(), table, fname, fromval);
			byte[] valfin = (toval != null) ? ByteUtil.buildKey(toval) : null;
			
			while (valb != null) {
				// check if past "To"
				if ((valfin != null) && (ByteUtil.compareKeys(valb, valfin) > 0))		// inclusive
					break;
				
				Object val = ByteUtil.extractValue(valb);

				byte[] recid = request.getInterface().nextPeerKey(did, ffdef.getIndexName(), table, fname, val, null);
				
				while (recid != null) {
					Object rid = ByteUtil.extractValue(recid);

					if (ffdef.isStaticScalar()) {
						ExpressionResult filterResult = out.check(this, scope, table, rid);

						if (! filterResult.resume)
							return;
					}
					else {
						byte[] recsid = request.getInterface().nextPeerKey(did, DB_GLOBAL_INDEX_SUB, table, fname, val, rid, null);

						while (recsid != null) {
							Object rsid = ByteUtil.extractValue(recsid);

							String range = request.getInterface().getAsString(did, DB_GLOBAL_INDEX_SUB, table, fname, val, rid, rsid);

							if (StringUtil.isEmpty(range) || (when == null)) {
								ExpressionResult filterResult = out.check(this, scope, table, rid);

								if (! filterResult.resume)
									return;
							}
							else {
								int pos = range.indexOf(':');

								BigDateTime from = null;
								BigDateTime to = null;

								if (pos == -1) {
									from = BigDateTime.parse(range);
								}
								else if (pos == 0) {
									to = BigDateTime.parse(range.substring(1));
								}
								else {
									from = BigDateTime.parse(range.substring(0, pos));
									to = BigDateTime.parse(range.substring(pos + 1));
								}

								if (((from == null) || (this.when.compareTo(from) >= 0)) && ((to == null) || (this.when.compareTo(to) < 0))) {
									ExpressionResult filterResult = out.check(this, scope, table, rid);

									if (! filterResult.resume)
										return;
								}
							}

							recsid = request.getInterface().nextPeerKey(did, DB_GLOBAL_INDEX_SUB, table, fname, val, rsid);
						}
					}

					recid = request.getInterface().nextPeerKey(did, ffdef.getIndexName(), table, fname, val, rid);
				}
				
				valb = request.getInterface().nextPeerKey(did, ffdef.getIndexName(), table, fname, val);
			}
		}
		catch (Exception x) {
			Logger.error("traverseIndex error: " + x);
		}
	}
	
	// traverse the record ids, going backwards in values and ids
	public void traverseIndexReverseRange(IVariableAware scope, String table, String fname, Object fromval, Object toval, IFilter out) throws OperatingContextException {
		String did = this.request.getTenant();
		
		SchemaResource schema = ResourceHub.getResources().getSchema();
		DbField ffdef = schema.getDbField(table, fname);
		
		if (ffdef == null)
			return;
		
		DataType dtype = schema.getType(ffdef.getTypeId());
		
		if (dtype == null)
			return;
		
		fromval = dtype.toIndex(fromval, "eng");	// TODO increase locale support
		
		toval = dtype.toIndex(toval, "eng");	// TODO increase locale support
		
		try {
			byte[] valb = request.getInterface().getOrPrevPeerKey(did, ffdef.getIndexName(), table, fname, fromval);
			byte[] valfin = (toval != null) ? ByteUtil.buildKey(toval) : null;
			
			while (valb != null) {
				// check if past "To"
				if ((valfin != null) && (ByteUtil.compareKeys(valb, valfin) < 0))		// inclusive
					break;
				
				Object val = ByteUtil.extractValue(valb);
				
				byte[] recid = request.getInterface().prevPeerKey(did, ffdef.getIndexName(), table, fname, val, null);
				
				while (recid != null) {
					Object rid = ByteUtil.extractValue(recid);
					
					if (ffdef.isStaticScalar()) {
						ExpressionResult filterResult = out.check(this, scope, table, rid);

						if (! filterResult.resume)
							return;
					}
					else {
						byte[] recsid = request.getInterface().prevPeerKey(did, DB_GLOBAL_INDEX_SUB, table, fname, val, rid, null);

						while (recsid != null) {
							Object rsid = ByteUtil.extractValue(recsid);

							String range = request.getInterface().getAsString(did, DB_GLOBAL_INDEX_SUB, table, fname, val, rid, rsid);

							if (StringUtil.isEmpty(range) || (when == null)) {
								ExpressionResult filterResult = out.check(this, scope, table, rid);

								if (! filterResult.resume)
									return;
							}
							else {
								int pos = range.indexOf(':');

								BigDateTime from = null;
								BigDateTime to = null;

								if (pos == -1) {
									from = BigDateTime.parse(range);
								}
								else if (pos == 0) {
									to = BigDateTime.parse(range.substring(1));
								}
								else {
									from = BigDateTime.parse(range.substring(0, pos));
									to = BigDateTime.parse(range.substring(pos + 1));
								}

								if (((from == null) || (this.when.compareTo(from) >= 0)) && ((to == null) || (this.when.compareTo(to) < 0))) {
									ExpressionResult filterResult = out.check(this, scope, table, rid);

									if (! filterResult.resume)
										return;
								}
							}

							recsid = request.getInterface().prevPeerKey(did, DB_GLOBAL_INDEX_SUB, table, fname, val, rsid);
						}
					}

					recid = request.getInterface().prevPeerKey(did, ffdef.getIndexName(), table, fname, val, rid);
				}
				
				valb = request.getInterface().prevPeerKey(did, ffdef.getIndexName(), table, fname, val);
			}
		}
		catch (Exception x) {
			Logger.error("traverseIndex error: " + x);
		}
	}
	
	public void executeTrigger(String table, String id, String op, Struct context) {
		SchemaResource schema = ResourceHub.getResources().getSchema();
		List<DbTrigger> trigs = schema.getDbTriggers(table, op);
		
		for (DbTrigger trig : trigs) {
			String spname = trig.execute;
			
			try {
				Class<?> spclass = Class.forName(spname);				
				ITrigger sp = (ITrigger) spclass.newInstance();
				sp.execute(this, table, id, context);
			} 
			catch (Exception x) {
				Logger.error("Unable to load/start trigger class: " + x);
			}
		}
	}
	
	/*
 ;	TODO improve so source can be table or scriptold
 
 ; Params("Sources",[table name],"Title")=[field name]
 ; Params("Sources",[table name],"Body")=[field name]
 ; Params("Sources",[table name],"Extras",[field name])=1  
 ;
 		TODO wrap up to above sids are part of a source
 ; Params("AllowedSids",[table name],[field name],[sid])=1					- if field name not present then assume all
 ;
 ; Params("RequiredWords",[word],"Term")=1
 ;                              ,"Exact")=[exact word for final match in Original]
 ;
 ; Params("AllowedWords",[word],"Term")=1
 ;                              ,"Exact")=[exact word for final match in Original]
 ;
 ; Params("ProhibitedWords",[word],"Term")=1
 ;                              ,"Exact")=[exact word for final match in Original]
 ;
 ; returns top results first, returns only 1000 
 ;   and no more than 100 or so per field...that is all one should ever need
 ;
 ; RETURN = [
 ;		{
 ;			Table: N,
 ;			Id: N,
 ;			Score: N,
 ;			TitlePositions: [ N, n ],		// relative to Title, where 0 is first character
 ;			Title: SSSS
 ;			BodyPositions: [ N, n ],		// relative to Body, where 0 is first character
 ;			Body: SSSS
 ;		}
 ; ]
 ;
srchTxt n params2,table,field,ret,match,score,id,ttitle,tbody,tab,sid,word,plist,i
 ;
 ; srchTxt2 is the heart of the searching, this routine just formats those results
 ; so we need to prep some parameters for srchTxt2
 ;
 m params2("RequiredWords")=Params("RequiredWords")
 m params2("AllowedWords")=Params("AllowedWords")
 m params2("ProhibitedWords")=Params("ProhibitedWords")
 m params2("AllowedSids")=Params("AllowedSids")
 ;
 ; convert sources to the srchTxt2 structure
 ;
 f  s table=$o(Params("Sources",table))  q:table=""  d
 . s ttitle=Params("Sources",table,"Title")
 . s:ttitle'="" params2("Sources",table,ttitle)=1
 . ;
 . s tbody=Params("Sources",table,"Body")
 . s:tbody'="" params2("Sources",table,tbody)=1
 . ;
 . k field  f  s field=$o(Params("Sources",table,"Extras",field))  q:field=""  d
 . . s params2("Sources",table,field)=1
 ;
 ; collect search results
 ;
 d srchTxt2(.params2,.ret)
 ;
 ; return the results
 ;
 w StartList
 ;
 f  s score=$o(ret(score),-1)  q:score=""  d
 . f  s table=$o(ret(score,table))  q:table=""  d
 . . s tab=$p(table,"#",1)
 . . f  s id=$o(ret(score,table,id))  q:id=""  d
 . . . w StartRec
 . . . w Field_"Table"_ScalarStr_tab
 . . . w Field_"Id"_ScalarStr_id
 . . . w Field_"Score"_ScalarInt_score
 . . . ;
 . . . s ttitle=Params("Sources",tab,"Title")
 . . . s tbody=Params("Sources",tab,"Body")
 . . . ;
 . . . w Field_"Title"_ScalarStr
 . . . s sid=$o(^dcTextRecord(table,id,ttitle,""))
 . . . ;
 . . . i sid'="" d
 . . . . w ^dcTextRecord(table,id,ttitle,sid,"Original",0)		; titles are no more than one line
 . . . . ;
 . . . . i $d(ret(score,table,id,ttitle,sid))>0  d
 . . . . . w Field_"TitlePositions"_StartList
 . . . . . ;
 . . . . . f  s word=$o(ret(score,table,id,ttitle,sid,word))  q:word=""  d
 . . . . . . s plist=ret(score,table,id,ttitle,sid,word)
 . . . . . . f i=1:1:$l(plist,",") w ScalarInt_$p(plist,",",i)
 . . . . . ;
 . . . . . w EndList
 . . . . ;
 . . . s tbody=Params("Sources",tab,"Body")
 . . . ;
 . . . w Field_"Body"_ScalarStr
 . . . k sid  f  s sid=$o(^dcTextRecord(table,id,tbody,sid))  q:sid=""  d
 . . . . ;
 . . . . ;i $d(ret(score,table,id,tbody,sid))>0  d  q
 . . . . ;. ; TODO find the positions and cut out parts
 . . . . ;
 . . . . ; if we get here then we are just writing the top 30 words
 . . . . s sentence=^dcTextRecord(table,id,tbody,sid,"Original",0)
 . . . . k wcnt  f i=1:1:$l(sentence) q:wcnt=30  i $e(sentence,i)=" " s wcnt=wcnt+1
 . . . . w $e(sentence,1,i-1)
 . . . ;
 . . . w EndRec
 ;
 w EndList
 ;
 quit
 ;
 ;
 ; Params("Sources",[table name],[field name])=1  
 ;
 ; Params("AllowedSids",[table name],[field name],[sid])=1					- if field name not present then assume all
 ;				TODO AllowedSids not yet coded!!
 ; Params("RequiredWords",[word],"Term")=1
 ;                              ,"Exact")=[exact word for final match in Original]
 ;
 ; Params("AllowedWords",[word],"Term")=1
 ;                              ,"Exact")=[exact word for final match in Original]
 ;
 ; Params("ProhibitedWords",[word],"Term")=1
 ;                              ,"Exact")=[exact word for final match in Original]
 ;
 ; ret
 ;		ret(score,table,id,field,sid,word)=[comma list of positions]
 ;
srchTxt2(params,ret) n score,table,field,id,sid,pos,word,sources,find,fnd,sscore,tabled
 n exact,lnum,matches,fmatch,ismatch,word2,entry,term,nxtscore,collect
 ;
 ; create one list of all words we are searching for
 ;
 f  s word=$o(params("RequiredWords",word))  q:word=""  s find(word)=1,lnum=lnum+1
 f  s word=$o(params("AllowedWords",word))  q:word=""  s find(word)=1,lnum=lnum+1
 ;
 s lnum=$s(lnum>5:20,lnum>3:50,1:100)    ; limit how many partial matches we look at if we have many words
 ;
 ; prime the sources array - we want the top scoring word for each table and field
 ; we'll then use this array to figure the top score of all.  as we loop the sources
 ; we'll keep adding more top matches so we find the next top scoring
 ;
 f  s table=$o(params("Sources",table))  q:table=""  d
 . i (table'["#")&(Domain'="") s tabled=table_"#"_Domain     ; support table instances
 . e  s tabled=table
 . ;
 . f  s field=$o(params("Sources",table,field))  q:field=""  d
 . . f  s word=$o(find(word))  q:word=""  d
 . . . s score=$o(^dcTextIndex(tabled,field,word,""),-1)  
 . . . i score'="" s sources(score,word,tabled,field)=1  						; sources will get filled out further down
 . . . ;
 . . . i (params("RequiredWords",word,"Exact")'="")!(params("AllowedWords",word,"Exact")'="") q
 . . . k matches 
 . . . s word2=word
 . . . f  s word2=$o(^dcTextIndex(tabled,field,word2))  q:word2=""  d  q:matches>(lnum-1) 
 . . . . i $f(word2,word)'=($l(word)+1) s matches=lnum q	; if not starting with the original word then stop looking
 . . . . s score=$o(^dcTextIndex(tabled,field,word2,""),-1)  
 . . . . i score'="" s sources(score,word2,tabled,field)=1,matches=matches+1
 ;
 ; find our top scoring fields/words and then use the text index to find possible
 ; record matches.
 ;
 k score,matches
 f  s score=$o(sources(score),-1)  q:score=""  d  q:matches>999
 . f  s word=$o(sources(score,word))  q:word=""  d
 . . f  s table=$o(sources(score,word,table))  q:table=""  d
 . . . k field
 . . . f  s field=$o(sources(score,word,table,field))  q:field=""!(fmatch(table,field)>99)  d
 . . . . k id
 . . . . f  s id=$o(^dcTextIndex(table,field,word,score,id))  q:id=""!(fmatch(table,field)>99)  d
 . . . . . k sid
 . . . . . f  s sid=$o(^dcTextIndex(table,field,word,score,id,sid))  q:sid=""!(fmatch(table,field)>99)  d
 . . . . . . ; check exact matches - if a required or allowed word is to have an exact match
 . . . . . . ;
 . . . . . . k ismatch
 . . . . . . ;
 . . . . . . i params("RequiredWords",word) d  i 1
 . . . . . . . s exact=params("RequiredWords",word,"Exact")  i exact="" s ismatch=1 q
 . . . . . . . k lnum  f  s lnum=$o(^dcTextRecord(table,id,field,sid,"Original",lnum))  q:lnum=""  d  q:ismatch
 . . . . . . . . i ^dcTextRecord(table,id,field,sid,"Original",lnum)[exact s ismatch=1 
 . . . . . . ; 
 . . . . . . e  i params("AllowedWords",word) d  i 1
 . . . . . . . s exact=params("AllowedWords",word,"Exact")  i exact="" s ismatch=1 q
 . . . . . . . k lnum  f  s lnum=$o(^dcTextRecord(table,id,field,sid,"Original",lnum))  q:lnum=""  d  q:ismatch
 . . . . . . . . i ^dcTextRecord(table,id,field,sid,"Original",lnum)[exact s ismatch=1 
 . . . . . . ;
 . . . . . . e  s ismatch=1
 . . . . . . ;
 . . . . . . q:'ismatch
 . . . . . . ;
 . . . . . . ; check prohibited - see if a prohibited word is in a match
 . . . . . . ; 
 . . . . . . k word2  f  s word2=$o(params("ProhibitedWords",word2))  q:word2=""  d  q:'ismatch
 . . . . . . . s exact=params("ProhibitedWords",word2,"Exact")  
 . . . . . . . s entry=$s(exact="":"Analyzed",1:"Original"),term=$s(exact="":"|"_word2_":",1:word2)
 . . . . . . . k lnum  f  s lnum=$o(^dcTextRecord(table,id,field,sid,entry,lnum))  q:lnum=""  d  q:'ismatch
 . . . . . . . . i ^dcTextRecord(table,id,field,sid,term,lnum)[term s ismatch=0 
 . . . . . . ;
 . . . . . . q:'ismatch
 . . . . . . ;
 . . . . . . s collect(table,id,field,sid,word)=score		; collect contains the values we need for ordering our results
 . . . . . . ;
 . . . . . . s fmatch(table,field)=fmatch(table,field)+1,matches=matches+1
 . . . . ; 
 . . . . s nxtscore=$o(^dcTextIndex(table,field,word,score),-1)  q:nxtscore=""
 . . . . s sources(nxtscore,word,table,field)=1									; filling out sources 
 ;
 ; build return value - we now have enough words, just want to put them in the right order
 ;
 k table,field,id,sid,word,matches
 ;
 f  s table=$o(collect(table))  q:table=""  d  q:matches>249
 . f  s id=$o(collect(table,id))  q:id=""  d  q:matches>249
 . . s ismatch=1
 . . ;
 . . ; ensure all required are present - unlike prohibited, which we can check above,
 . . ; we have to check required across all potential fields for a given record
 . . ; 
 . . k word2  f  s word2=$o(params("RequiredWords",word2))  q:word2=""  d  q:'ismatch
 . . . q:word=word2  ; already checked
 . . . s exact=params("RequiredWords",word2,"Exact"),fnd=0  
 . . . s entry=$s(exact="":"Analyzed",1:"Original"),term=$s(exact="":"|"_word2_":",1:word2)
 . . . ;
 . . . ; check all fields/sids
 . . . ; 
 . . . k field  f  s field=$o(params("Sources",$p(table,"#",1),field))  q:field=""  d  q:fnd
 . . . . k sid  f  s sid=$o(^dcTextRecord(table,id,field,sid))  q:sid=""  d  q:fnd
 . . . . . k lnum  f  s lnum=$o(^dcTextRecord(table,id,field,sid,entry,lnum))  q:lnum=""  d  q:fnd
 . . . . . . i ^dcTextRecord(table,id,field,sid,entry,lnum)[term s fnd=1 
 . . . ;
 . . . s:'fnd ismatch=0
 . . ;
 . . q:'ismatch 
 . . ;
 . . ; compute score for the record
 . . s score=0
 . . ;
 . . k field  f  s field=$o(collect(table,id,field))  q:field=""  d  
 . . . k sid  f  s sid=$o(collect(table,id,field,sid))  q:sid=""  d
 . . . . k word  f  s word=$o(collect(table,id,field,sid,word))  q:word=""  d
 . . . . . s sscore=collect(table,id,field,sid,word)
 . . . . . ;
 . . . . . ; bonus if the word we are scoring matches one of the original words
 . . . . . i params("AllowedWords",word)!params("RequiredWords",word) d
 . . . . . . s lnum=collect(table,id,field,sid,word)
 . . . . . . s sscore=sscore+($l(^dcTextIndex(table,field,word,lnum,id,sid),",")*2)           ; bonus for each word occurance
 . . . . . ;
 . . . . . s score=score+sscore
 . . ;
 . . ; we now have the score for the record
 . . ;
 . . k field  f  s field=$o(collect(table,id,field))  q:field=""  d   
 . . . k sid  f  s sid=$o(collect(table,id,field,sid))  q:sid=""  d
 . . . . k word  f  s word=$o(collect(table,id,field,sid,word))  q:word=""  d
 . . . . . s lnum=collect(table,id,field,sid,word)
 . . . . . s ret(score,table,id,field,sid,word)=^dcTextIndex(table,field,word,lnum,id,sid)
 . . . ;
 . . s matches=matches+1    ; record matches, not word matches
 ;
 quit
 ;
 ;
 	 * 
	 */
	
	/* TODO must run in each tenant
	public void rebuildIndexes() {
		this.rebuildIndexes(TenantHub.resolveTenant(this.request.getTenant()), BigDateTime.nowDateTime());
	}

	public void rebuildIndexes(Tenant di, BigDateTime when) {
		try {
			for (DbTable tbl : di.getSchema().getDbTables()) {
				this.rebuildTableIndex(di, tbl.getName(), when);
			}
			
			/*
			byte[] traw = this.request.getInterface().nextPeerKey(DB_GLOBAL_RECORD, di.getId(), null);
			
			while (traw != null) {
				Object table = ByteUtil.extractValue(traw);
				
				this.rebuildTableIndex(di, table.toString(), when);
				
				traw = this.request.getInterface().nextPeerKey(DB_GLOBAL_RECORD, di.getId(), table);
			}
			* /
		}
		catch (Exception x) {
			Logger.error("rebuildTenantIndexes error: " + x);
		}
		finally {
			task.popTenant();
		}
	}

	public void rebuildTableIndex(String table) {
		this.rebuildTableIndex(TenantHub.resolveTenant(this.request.getTenant()), table, BigDateTime.nowDateTime());
	}
	
	public void rebuildTableIndex(Tenant di, String table, BigDateTime when) {
		try {
			// kill the indexes
			this.request.getInterface().kill(DB_GLOBAL_INDEX_SUB, di.getId(), table);
			this.request.getInterface().kill(DB_GLOBAL_INDEX, di.getId(), table);
			
			// see if there is even such a table in the schema
			//DomainInfo di = this.dm.getDomainInfo(did);
			
			
			
			if (!di.getSchema().hasTable(table)) {
				System.out.println("Skipping table, not known by this domain: " + table);
			}
			else {
				System.out.println("Indexing table: " + table);
				
				this.traverseRecords(table, when, false, new Function<Object,Boolean>() {
					@Override
					public Boolean apply(Object id) {
						
						for (DbField schema : di.getRootSite().getResources().getSchema().getDbFields(table)) {
							if (!schema.isIndexed())
								continue;
							
							String did = di.getId();
							
							try {
								// --------------------------------------
								// StaticScalar handling 
								// --------------------------------------
								if (!schema.isList() && !schema.isDynamic()) {
									
									// find the first, newest, stamp 
									byte[] nstamp = TablesAdapter.this.request.getInterface().nextPeerKey(DB_GLOBAL_RECORD, did, table, id, schema.getName(), null);
									
									if (nstamp == null)
										continue;
									
									BigDecimal stamp = Struct.objectToDecimal(ByteUtil.extractValue(nstamp));
									
									if (stamp == null)
										continue;
									
									if (TablesAdapter.this.request.getInterface().getAsBooleanOrFalse(DB_GLOBAL_RECORD, did, table, id, schema.getName(), stamp, "Retired"))
										continue;
									
									if (!TablesAdapter.this.request.getInterface().isSet(DB_GLOBAL_RECORD, did, table, id, schema.getName(), stamp, "Data"))
										continue;
										
									Object value = TablesAdapter.this.request.getInterface().get(DB_GLOBAL_RECORD, did, table, id, schema.getName(), stamp, "Data");
									
									if (value instanceof String)
										value = value.toString().toLowerCase(Locale.ROOT);
								
									// increment index count
									// set the new index new
									TablesAdapter.this.request.getInterface().inc(DB_GLOBAL_INDEX, did, table, schema.getName(), value);
									TablesAdapter.this.request.getInterface().set(DB_GLOBAL_INDEX, did, table, schema.getName(), value, id, null);
								}				
								else {
									TablesAdapter.this.traverseSubIds(table, id.toString(), schema.getName(), when, false, new Function<Object,Boolean>() {
										@Override
										public Boolean apply(Object sid) {
											try {
												// find the first, newest, stamp 
												byte[] nstamp = TablesAdapter.this.request.getInterface().nextPeerKey(DB_GLOBAL_RECORD, did, table, id, schema.getName(), sid, null);
												
												if (nstamp == null)
													return false;
												
												BigDecimal stamp = Struct.objectToDecimal(ByteUtil.extractValue(nstamp));
												
												if (stamp == null)
													return false;
												
												if (TablesAdapter.this.request.getInterface().getAsBooleanOrFalse(DB_GLOBAL_RECORD, did, table, id, schema.getName(), sid, stamp, "Retired"))
													return false;
												
												if (!TablesAdapter.this.request.getInterface().isSet(DB_GLOBAL_RECORD, did, table, id, schema.getName(), sid, stamp, "Data"))
													return false;
														
												Object value = TablesAdapter.this.request.getInterface().get(DB_GLOBAL_RECORD, did, table, id, schema.getName(), sid, stamp, "Data");
												Object from = TablesAdapter.this.request.getInterface().get(DB_GLOBAL_RECORD, did, table, id, schema.getName(), sid, stamp, "From");
												Object to = TablesAdapter.this.request.getInterface().get(DB_GLOBAL_RECORD, did, table, id, schema.getName(), sid, stamp, "To");

												--
												if (value instanceof String)
													value = value.toString().toLowerCase(Locale.ROOT);
												
												String range = null;
												
												if (from != null)
													range = from.toString();
												
												if (to != null) {
													if (range == null)
														range = ":" + to.toString();
													else
														range += ":" + to.toString();
												}
												
												// increment index count
												// set the new index new
												TablesAdapter.this.request.getInterface().inc(DB_GLOBAL_INDEX_SUB, did, table, schema.getName(), value);
												TablesAdapter.this.request.getInterface().set(DB_GLOBAL_INDEX_SUB, did, table, schema.getName(), value, id, sid, range);
												
												return true;
											}
											catch (Exception x) {
												System.out.println("Error indexing table: " + table + " - " + schema.getName() + " - " + id + " - " + sid + ": " + x);
											}
											
											return false;
										}
									});									
								}
							}
							catch (Exception x) {
								System.out.println("Error indexing table: " + table + " - " + schema.getName() + " - " + id + ": " + x);
							}
						}
						
						return true;
					}
				});
			}
		} 
		catch (DatabaseException x) {
			System.out.println("Error indexing table: " + table + ": " + x);
		}
	}		
	*/
}
