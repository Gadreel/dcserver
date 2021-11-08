package dcraft.db.tables;

import static dcraft.db.Constants.*;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;

import dcraft.db.DatabaseException;
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
import dcraft.struct.BaseStruct;
import dcraft.struct.FieldStruct;
import dcraft.struct.RecordStruct;
import dcraft.struct.Struct;
import dcraft.util.StringUtil;

public class TablesAdapter {
	static public TablesAdapter of(IRequestContext request) {
		TablesAdapter adapter = new TablesAdapter();
		adapter.request = request;
		return adapter;
	}

	protected IRequestContext request = null;

	// don't call for general code...
	protected TablesAdapter() {
	}

	public IRequestContext getRequest() {
		return this.request;
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
				BaseStruct value = data.getField("Data");
				
				if (value == null) {
					if (schema.isRequired()) 
						Logger.error("Field cannot be null: " + table + " - " + schema.getName());
					
					return;
				}

				BaseStruct cor = SchemaHub.normalizeValidateType(true, false, value, schema.getTypeId());
				
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
					// Scalar handling - Data or Retired (true) not both
					// --------------------------------------
					if (schema.isScalar()) {
						fieldChecker.accept(schema, (RecordStruct) field.getValue());
					}
					// --------------------------------------
					// List handling
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
				// Scalar handling - Data or Retired (true) not both
				//
				//   fields([field name],"Data") = [value]
				//   fields([field name],"Lang") = [value]
				//   fields([field name],"UpdateOnly") = [value]
				//   fields([field name],"Retired") = [value]
				// --------------------------------------
				if (schema.isScalar()) {
					RecordStruct data = (RecordStruct) field.getValue();
					
					Object newValue = Struct.objectToCore(data.getField("Data"));
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
						if ((oldIsSet && auditDisabled) || (ByteUtil.compareKeys(olderStamp, newerStamp) == 0)) {
							this.request.getInterface().kill(did, DB_GLOBAL_RECORD, table, id, fname, stamp, "Data");
							this.request.getInterface().kill(did, DB_GLOBAL_RECORD, table, id, fname, stamp, "Search");
							this.request.getInterface().kill(did, DB_GLOBAL_RECORD, table, id, fname, stamp, "Index");
							this.request.getInterface().kill(did, DB_GLOBAL_RECORD, table, id, fname, stamp, "Lang");
						}
						
						this.request.getInterface().set(did, DB_GLOBAL_RECORD, table, id, fname, stamp, "Retired", retired);
					}
					else {						
						if (auditDisabled || (ByteUtil.compareKeys(olderStamp, newerStamp) == 0))
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
				// List handling
				//   fields([field name],sid,"Data") = [value]
				//   fields([field name],sid,"Lang") = [value]
				//   fields([field name],sid,"UpdateOnly") = [value]
				//   fields([field name],sid,"Retired") = [value]			|value1|value2|etc...
				// --------------------------------------
				
				for (FieldStruct subid : ((RecordStruct) field.getValue()).getFields()) {
					String sid = subid.getName();
					
					RecordStruct data = (RecordStruct) subid.getValue();
					
					Object newValue = Struct.objectToCore(data.getField("Data"));
					String lang = data.getFieldAsString("Lang", locale.getDefaultLocale());
					Object newIdxValue = dtype.toIndex(newValue, lang);
					
					boolean isTenantLang = lang.equalsIgnoreCase(locale.getDefaultLocale());
					boolean retired = data.getFieldAsBooleanOrFalse("Retired");
					boolean updateOnly = data.getFieldAsBooleanOrFalse("UpdateOnly");

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
						if ((auditDisabled && oldIsSet) || (ByteUtil.compareKeys(olderStamp, newerStamp) == 0)) {
							this.request.getInterface().kill(did, DB_GLOBAL_RECORD, table, id, fname, sid, stamp, "Data");
							this.request.getInterface().kill(did, DB_GLOBAL_RECORD, table, id, fname, sid, stamp, "Search");
							this.request.getInterface().kill(did, DB_GLOBAL_RECORD, table, id, fname, sid, stamp, "Index");
							this.request.getInterface().kill(did, DB_GLOBAL_RECORD, table, id, fname, sid, stamp, "Lang");
						}
						
						this.request.getInterface().set(did, DB_GLOBAL_RECORD, table, id, fname, sid, stamp, "Retired", retired);
					}
					else {
						// if we are not retiring then get rid of old Retired just in case it was set before
						if (auditDisabled || (ByteUtil.compareKeys(olderStamp, newerStamp) == 0))
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
						// increment index count
						// set the new index new
						this.request.getInterface().inc(did, DB_GLOBAL_INDEX_SUB, table, fname, newIdxValue);
						this.request.getInterface().set(did, DB_GLOBAL_INDEX_SUB, table, fname, newIdxValue, id, sid, null);
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
				
				//Logger.info(" - Record: " + id);
				
				TablesAdapter.this.indexCleanFields(tableschema, id);
				
				return ExpressionResult.accepted();
			}
		});
	}

	public void indexCleanRecords(IVariableAware scope, TableView tableschema, DbField fldschema) throws OperatingContextException {
		this.traverseRecords(scope, tableschema.getName(), new BasicFilter() {
			@Override
			public ExpressionResult check(TablesAdapter adapter, IVariableAware scope, String table, Object val) throws OperatingContextException {
				OperationContext.getOrThrow().touch();

				String id = val.toString();

				//Logger.info(" - Record: " + id);

				TablesAdapter.this.indexCleanField(tableschema, fldschema, id);

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
			// Scalar handling - Data or Retired (true) not both
			//
			//   fields([field name],"Data") = [value]
			//   fields([field name],"Lang") = [value]
			//   fields([field name],"UpdateOnly") = [value]
			//   fields([field name],"Retired") = [value]
			// --------------------------------------
			if (schema.isScalar()) {
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
			// List handling
			//   fields([field name],sid,"Data") = [value]
			//   fields([field name],sid,"Lang") = [value]
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

							if (schema.isIndexed() && (newIdxValue != null)) {
								this.request.getInterface().inc(did, DB_GLOBAL_INDEX_SUB, tableschema.getName(), fname, newIdxValue);
								this.request.getInterface().set(did, DB_GLOBAL_INDEX_SUB, tableschema.getName(), fname, newIdxValue, id, sid, null);
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

	public void clearIndexRecord(TableView tableschema, String id) throws OperatingContextException {
		if (tableschema == null) {
			Logger.error("Table not defined");
			return;
		}

		for (DbField field : tableschema.getFields()) {
			if (! field.isIndexed())
				continue;

			// TODO if debug
			Logger.info("   - Field: " + field.getName());

			this.clearIndexField(tableschema, id, field);
		}
	}

	public void clearIndexField(TableView tableschema, String id, DbField schema) throws OperatingContextException {
		String did = this.request.getTenant();

		if (tableschema == null) {
			Logger.error("Table not defined");
			return;
		}

		if (schema == null) {
			Logger.error("Table field not defined: " + tableschema.getName());
			return;
		}

		SchemaResource schemares = ResourceHub.getResources().getSchema();
		DataType dtype = schemares.getType(schema.getTypeId());

		LocaleResource locale = OperationContext.getOrThrow().getTenant().getResources().getLocale();

		// --------------------------------------------------
		//   index updates herein may have race condition issues with the value counts
		//   this is ok as counts are just used for suggestions anyway
		// --------------------------------------------------

		String tname = tableschema.getName();
		String fname = schema.getName();

		try {
			// --------------------------------------
			// Scalar handling - Data or Retired (true) not both
			//
			//   fields([field name],"Data") = [value]
			//   fields([field name],"Lang") = [value]
			//   fields([field name],"UpdateOnly") = [value]
			//   fields([field name],"Retired") = [value]
			// --------------------------------------
			if (schema.isScalar()) {
				// find the first, newest, stamp
				byte[] stampraw = this.request.getInterface().nextPeerKey( did, DB_GLOBAL_RECORD, tname, id, fname, null);

				if (stampraw == null)
					return;

				BigDecimal stamp = Struct.objectToDecimal(ByteUtil.extractValue(stampraw));

				if (stamp == null)
					return;

				// try to get the data if any - note retired fields have no data
				boolean isRetired = this.request.getInterface().getAsBooleanOrFalse(did, DB_GLOBAL_RECORD, tname, id, fname, stamp, "Retired");

				if (isRetired)
					return;

				Object idxValue = this.request.getInterface().get(did, DB_GLOBAL_RECORD, tname, id, fname, stamp, "Index");

				if (idxValue == null) {
					Object value = this.request.getInterface().get(did, DB_GLOBAL_RECORD, tname, id, fname, stamp, "Data");

					if (value == null)
						return;

					String lang = locale.getDefaultLocale();

					if (this.request.getInterface().isSet(did, DB_GLOBAL_RECORD, tname, id, fname, stamp, "Lang"))
						lang = Struct.objectToString(this.request.getInterface().get(did, DB_GLOBAL_RECORD, tname, id, fname, stamp, "Lang"));

					idxValue = dtype.toIndex(value, lang);
				}

				if ((idxValue != null) && this.request.getInterface().isSet(did, DB_GLOBAL_INDEX, tname, fname, idxValue, id)) {
					// decrement index count for the old value
					// remove the old index value
					this.request.getInterface().dec(did, DB_GLOBAL_INDEX, tname, fname, idxValue);
					this.request.getInterface().kill(did, DB_GLOBAL_INDEX, tname, fname, idxValue, id);
				}

				return;
			}

			// --------------------------------------
			//
			// Handling for other types
			//
			// List handling
			//   fields([field name],sid,"Data") = [value]
			//   fields([field name],sid,"Lang") = [value]
			//   fields([field name],sid,"Retired") = [value]			|value1|value2|etc...
			// --------------------------------------
			// find the first, newest, stamp

			byte[] subraw = this.request.getInterface().nextPeerKey( did, DB_GLOBAL_RECORD, tname, id, fname, null);

			while (subraw != null) {
				String sid = Struct.objectToString(ByteUtil.extractValue(subraw));

				// error
				if (sid == null)
					return;

				// STAY IN LOOP until we run out of sub ids

				// find the first, newest, stamp
				byte[] stampraw = this.request.getInterface().nextPeerKey(did, DB_GLOBAL_RECORD, tname, id, fname, sid, null);

				if (stampraw != null) {
					BigDecimal stamp = Struct.objectToDecimal(ByteUtil.extractValue(stampraw));

					if (stamp != null) {
						// try to get the data if any - note retired fields have no data
						boolean isRetired = this.request.getInterface().getAsBooleanOrFalse(did, DB_GLOBAL_RECORD, tname, id, fname, sid, stamp, "Retired");

						if (! isRetired) {
							Object idxValue = this.request.getInterface().get(did, DB_GLOBAL_RECORD, tname, id, fname, sid, stamp, "Index");

							if (idxValue == null) {
								Object value = this.request.getInterface().get(did, DB_GLOBAL_RECORD, tname, id, fname, sid, stamp, "Data");

								if (value != null) {
									String lang = locale.getDefaultLocale();

									if (this.request.getInterface().isSet(did, DB_GLOBAL_RECORD, tname, id, fname, sid, stamp, "Lang"))
										lang = Struct.objectToString(this.request.getInterface().get(did, DB_GLOBAL_RECORD, tname, id, fname, sid, stamp, "Lang"));

									idxValue = dtype.toIndex(value, lang);
								}
							}

							if ((idxValue != null) && this.request.getInterface().isSet(did, DB_GLOBAL_INDEX_SUB, tname, fname, idxValue, id, sid)) {
								// decrement index count for the old value
								// remove the old index value
								this.request.getInterface().dec(did, DB_GLOBAL_INDEX_SUB, tname, fname, idxValue);
								this.request.getInterface().kill(did, DB_GLOBAL_INDEX_SUB, tname, fname, idxValue, id, sid);
							}
						}
					}
				}

				subraw = this.request.getInterface().nextPeerKey( did, DB_GLOBAL_RECORD, tname, id, fname, sid);
			}
		}
		catch (Exception x) {
			Logger.error("Error indexing field: " + fname);
		}
	}

	public boolean setScalar(String table, String id, String field, Object data) throws OperatingContextException {
		RecordStruct fields = new RecordStruct()
				.with(field, new RecordStruct()
						.with("Data", data)
				);
		
		return this.setFields(table, id, fields);
	}
	
	public boolean updateScalar(String table, String id, String field, Object data) throws OperatingContextException {
		RecordStruct fields = new RecordStruct()
				.with(field, new RecordStruct()
						.with("Data", data)
						.with("UpdateOnly", true)
				);
		
		return this.setFields(table, id, fields);
	}
	
	public boolean retireScalar(String table, String id, String field) throws OperatingContextException {
		RecordStruct fields = new RecordStruct()
				.with(field, new RecordStruct()
						.with("Retired", true)
						.with("UpdateOnly", true)
				);
		
		return this.setFields(table, id, fields);
	}
	
	public boolean setList(String table, String id, String field, String subid, Object data) throws OperatingContextException {
		RecordStruct fields = new RecordStruct()
				.with(field, new RecordStruct()
						.with(subid, new RecordStruct().with("Data", data))
				);
		
		return this.setFields(table, id, fields);
	}
	
	public boolean updateList(String table, String id, String field, String subid, Object data) throws OperatingContextException {
		RecordStruct fields = new RecordStruct()
				.with(field, new RecordStruct()
						.with(subid, new RecordStruct()
								.with("Data", data)
								.with("UpdateOnly", true)
						)
				);
		
		return this.setFields(table, id, fields);
	}
	
	public boolean retireList(String table, String id, String field, String subid) throws OperatingContextException {
		RecordStruct fields = new RecordStruct()
				.with(field, new RecordStruct()
						.with(subid, new RecordStruct()
								.with("Retired", true)
								.with("UpdateOnly", true)
						)
				);
		
		return this.setFields(table, id, fields);
	}

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

			if (Struct.objectToBooleanOrFalse(this.getScalar(table, id, "Retired")))
				return true;
		} 
		catch (DatabaseException x) {
			// TODO logger
		}
		
		return false;
	}
	
	public boolean isCurrent(String table, String id) throws OperatingContextException {
		return ! this.isRetired(table, id);
	}
	
	public Object getScalar(String table, String id, String field) throws OperatingContextException {
		return this.getScalar(table, id, field, null);
	}
	
	public Object getScalar(String table, String id, String field, String format) throws OperatingContextException {
		byte[] val = this.getRaw(table, id, field);

		return TableUtil.normalizeFormatRaw(table, id, field, val, format);
	}

	public byte[] getRaw(String table, String id, String field) throws OperatingContextException {
		return this.getRaw(table, id, field, "Data");
	}

	public byte[] getRaw(String table, String id, String field, String area) throws OperatingContextException {
		// checks the Retired flag 
		BigDecimal stamp = this.getScalarStamp(table, id, field);
		
		if (stamp == null)
			return null;
		
		try {
			return this.request.getInterface().getRaw(this.request.getTenant(), DB_GLOBAL_RECORD, table, id, field, stamp, area);
		}
		catch (DatabaseException x) {
			Logger.error("getRaw error: " + x);
		}
		
		return null;
	}

	public Object getScalar(String table, String id, String field, BigDecimal stamp, String format) throws OperatingContextException {
		byte[] val = this.getRaw(table, id, field, stamp);

		return TableUtil.normalizeFormatRaw(table, id, field, val, format);
	}

	public byte[] getRaw(String table, String id, String field, BigDecimal stamp) throws OperatingContextException {
		return this.getRaw(table, id, field, stamp, "Data");
	}

	public byte[] getRaw(String table, String id, String field, BigDecimal stamp, String area) throws OperatingContextException {
		if (stamp == null)
			return null;

		try {
			return this.request.getInterface().getRaw(this.request.getTenant(), DB_GLOBAL_RECORD, table, id, field, stamp, area);
		}
		catch (DatabaseException x) {
			Logger.error("getRaw error: " + x);
		}

		return null;
	}

	public RecordStruct getScalarExtended(String table, String id, String field, String format) throws OperatingContextException {
		BigDecimal stamp = this.getScalarStamp(table, id, field);
		
		if (stamp == null)
			return null;
		
		try {
			return RecordStruct.record()
				.with("Data", this.getScalar(table, id, field, format))
				.with("Retired", this.request.getInterface().get(this.request.getTenant(), DB_GLOBAL_RECORD, table, id, field, stamp, "Retired"))
				.with("Stamp", stamp);
		}
		catch (DatabaseException x) {
			Logger.error("getScalarExtended error: " + x);
		}
		
		return null;
	}
	
	public BigDecimal getScalarStamp(String table, String id, String field) throws OperatingContextException {
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
			Logger.error("getScalar error: " + x);
		}
		
		return null;
	}
	
	public Object getList(String table, String id, String field, String subid) throws OperatingContextException {
		return this.getList(table, id, field, subid, null);
	}
	
	public Object getList(String table, String id, String field, String subid, String format) throws OperatingContextException {
		byte[] val = this.getListRaw(table, id, field, subid);
		
		return TableUtil.normalizeFormatRaw(table, id, field, val, format);
	}
	
	public byte[] getListRaw(String table, String id, String field, String subid) throws OperatingContextException {
		return this.getListRaw(table, id, field, subid, "Data");
	}
	
	public byte[] getListRaw(String table, String id, String field, String subid, String area) throws OperatingContextException {
		// checks the Retired flag 
		BigDecimal stamp = this.getListStamp(table, id, field, subid);
		
		if (stamp == null)
			return null;
		
		try {
			return this.request.getInterface().getRaw(this.request.getTenant(), DB_GLOBAL_RECORD, table, id, field, subid, stamp, area);
		}
		catch (DatabaseException x) {
			Logger.error("getListRaw error: " + x);
		}
		
		return null;
	}

	public Object getList(String table, String id, String field, String subid, BigDecimal stamp, String format) throws OperatingContextException {
		byte[] val = this.getListRaw(table, id, field, subid, stamp);

		return TableUtil.normalizeFormatRaw(table, id, field, val, format);
	}

	public byte[] getListRaw(String table, String id, String field, String subid, BigDecimal stamp) throws OperatingContextException {
		return this.getListRaw(table, id, field, subid, stamp, "Data");
	}

	public byte[] getListRaw(String table, String id, String field, String subid, BigDecimal stamp, String area) throws OperatingContextException {
		if (stamp == null)
			return null;

		try {
			return this.request.getInterface().getRaw(this.request.getTenant(), DB_GLOBAL_RECORD, table, id, field, subid, stamp, area);
		}
		catch (DatabaseException x) {
			Logger.error("getListRaw error: " + x);
		}

		return null;
	}

	public RecordStruct getListExtended(String table, String id, String field, String subid, String format) throws OperatingContextException {
		BigDecimal stamp = this.getListStamp(table, id, field, subid);
		
		if (stamp == null)
			return null;
		
		try {
			return RecordStruct.record()
				.with("SubId", subid)
				.with("Data", this.getList(table, id, field, subid, format))
				.with("Retired", this.request.getInterface().get(this.request.getTenant(), DB_GLOBAL_RECORD, table, id, field, subid, stamp, "Retired"))
				.with("Stamp", stamp);
		}
		catch (DatabaseException x) {
			Logger.error("getListExtended error: " + x);
		}
		
		return null;
	}
	
	public List<String> getListKeys(String table, String id, String field) throws OperatingContextException {
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
			Logger.error("getListKeys error: " + x);
		}
		
		return ret;
	}

	// return only if there is value that is valid now
	public BigDecimal getListStamp(String table, String id, String field, String subid) throws OperatingContextException {
		try {
			byte[] olderStamp = this.request.getInterface().getOrNextPeerKey(this.request.getTenant(), DB_GLOBAL_RECORD, table, id, field, subid, this.request.getStamp());
			
			if (olderStamp == null) 
				return null;
			
			BigDecimal oldStamp = Struct.objectToDecimal(ByteUtil.extractValue(olderStamp));
			
			if (this.request.getInterface().getAsBooleanOrFalse(this.request.getTenant(), DB_GLOBAL_RECORD, table, id, field, subid, oldStamp, "Retired"))
				return null;

			return oldStamp;
		}
		catch (DatabaseException x) {
			Logger.error("get stamp error: " + x);
		}
		
		return null;
	}

	// no checks, just full list
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
			Logger.error("getListStamps error: " + x);
		}

		return ret;
	}

	// no checks, just next
	public BigDecimal getScalarNextStamp(String table, String id, String field, ZonedDateTime from) throws OperatingContextException {
		return this.getScalarNextStamp(table, id, field, BigDecimal.valueOf(this.request.getInterface().inverseTime(from)));
	}

	// no checks, just next
	public BigDecimal getScalarNextStamp(String table, String id, String field, BigDecimal fromstamp) throws OperatingContextException {
		try {
			// prev because really we want newer "next" stamp, not older
			byte[] stamp = this.request.getInterface().nextPeerKey(this.request.getTenant(), DB_GLOBAL_RECORD, table, id, field, fromstamp);

			if (stamp != null) {
				Object sid = ByteUtil.extractValue(stamp);

				BigDecimal ret = Struct.objectToDecimal(sid);

				//System.out.println("found 1: " + id + " - " + field + " : " + ret.toPlainString());

				return ret;
			}
		}
		catch (DatabaseException x) {
			Logger.error("getScalarNextStamp error: " + x);
		}

		return null;
	}

	// no checks, just next
	public BigDecimal getListNextStamp(String table, String id, String field, String subid, ZonedDateTime from) throws OperatingContextException {
		return this.getListNextStamp(table, id, field, subid, BigDecimal.valueOf(this.request.getInterface().inverseTime(from)));
	}

	// no checks, just next
	public BigDecimal getListNextStamp(String table, String id, String field, String subid, BigDecimal fromstamp) throws OperatingContextException {
		try {
			// prev because really we want newer "next" stamp, not older
			byte[] stamp = this.request.getInterface().nextPeerKey(this.request.getTenant(), DB_GLOBAL_RECORD, table, id, field, subid, fromstamp);

			if (stamp != null) {
				Object sid = ByteUtil.extractValue(stamp);

				BigDecimal ret = Struct.objectToDecimal(sid);

				//System.out.println("found: " + id + " - " + field + " - " + subid + " : " + ret.toPlainString());

				return ret;
			}
		}
		catch (DatabaseException x) {
			Logger.error("getListNextStamp error: " + x);
		}

		return null;
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
		
		if (schema.isScalar()) {
			ret.add(this.getScalar(table, id, field, format));
			return ret;
		}

		if (StringUtil.isNotEmpty(subid)) {
			ret.add(this.getList(table, id, field, subid));
		}
		else {
			try {
				byte[] bsubid = this.request.getInterface().nextPeerKey(this.request.getTenant(), DB_GLOBAL_RECORD, table, id, field, null);
				
				while (bsubid != null) {
					Object sid = ByteUtil.extractValue(bsubid);
					
					ret.add(this.getList(table, id, field, Struct.objectToString(sid), format));

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
		
		if (schema.isScalar()) {
			ret.add(this.getScalarExtended(table, id, field, format));
			return ret;
		}

		if (StringUtil.isNotEmpty(subid)) {
			ret.add(this.getListExtended(table, id, field, subid, format));
		}
		else {
			try {
				byte[] bsubid = this.request.getInterface().nextPeerKey(this.request.getTenant(), DB_GLOBAL_RECORD, table, id, field, null);
				
				while (bsubid != null) {
					Object sid = ByteUtil.extractValue(bsubid);
					
					ret.add(this.getListExtended(table, id, field, Struct.objectToString(sid), format));

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
		
		if (schemafld.isScalar()) {
			ret.add(this.getRaw(table, id, field, area));
			return ret;
		}

		if (StringUtil.isNotEmpty(subid)) {
			ret.add(this.getListRaw(table, id, field, subid, area));
		}
		else {
			try {
				byte[] bsubid = this.request.getInterface().nextPeerKey(this.request.getTenant(), DB_GLOBAL_RECORD, table, id, field, null);
				
				while (bsubid != null) {
					Object sid = ByteUtil.extractValue(bsubid);
					
					ret.add(this.getListRaw(table, id, field, Struct.objectToString(sid), area));

					bsubid = this.request.getInterface().nextPeerKey(this.request.getTenant(), DB_GLOBAL_RECORD, table, id, field, sid);
				}
			}
			catch (DatabaseException x) {
				Logger.error("getRaw error: " + x);
			}
		}
		
		return ret;
	}

	// retire means disable
	public void retireRecord(String table, String id) throws OperatingContextException {
		this.executeTrigger(table, id,"BeforeRetire", null);

		if (! this.isRetired(table, id)) {
			this.setScalar(table, id, "Retired", true);
			
			this.executeTrigger(table, id,"AfterRetire", null);
		}
	}

	public void reviveRecord(String table, String id) throws OperatingContextException {
		if (this.isRetired(table, id)) {
			this.setScalar(table, id, "Retired", false);
			
			this.executeTrigger(table, id, "AfterRevive",null);
		}
	}

	public void deleteRecord(String table, String id) throws OperatingContextException {
		if (this.executeCannotTrigger(table, id,"BeforeDeleteCheck", null))
			return;

		this.executeTrigger(table, id,"BeforeDelete", null);

		SchemaResource schemares = ResourceHub.getResources().getSchema();

		TableView tableView = schemares.getTableView(table);

		// needs to support clearing ForeignKey fields as well
		this.clearIndexRecord(tableView, id);

		try {
			this.request.getInterface().kill(this.request.getTenant(), DB_GLOBAL_RECORD, table, id);
		}
		catch (DatabaseException x) {
			Logger.error("remove record error: " + x);
		}

		this.executeTrigger(table, id,"AfterDelete", null);
	}

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

	public void traverseRecordsReverse(IVariableAware scope, String table, IFilter out) throws OperatingContextException {
		String did = this.request.getTenant();

		try {
			byte[] id = this.request.getInterface().prevPeerKey(did, DB_GLOBAL_RECORD, table, null);

			while (id != null) {
				Object oid = ByteUtil.extractValue(id);

				if (! out.check(this, scope, table, oid).resume)
					return;

				id = this.request.getInterface().prevPeerKey(did, DB_GLOBAL_RECORD, table, oid);
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

				if (ffdef.isScalar()) {
					ExpressionResult filterResult = out.check(this, scope, table, rid);

					if (! filterResult.resume)
						return out;
				}
				else {
					byte[] recsid = request.getInterface().nextPeerKey(did, DB_GLOBAL_INDEX_SUB, table, fname, val, rid, null);

					while (recsid != null) {
						Object rsid = ByteUtil.extractValue(recsid);

						if ((subid == null) || subid.equals(rsid)) {
							if (out instanceof IFilterSubAware) {
								ExpressionResult filterResult = ((IFilterSubAware) out).check(this, scope, table, rid, rsid);

								if (! filterResult.resume)
									return out;
							}
							else {
								ExpressionResult filterResult = out.check(this, scope, table, rid);

								if (! filterResult.resume)
									return out;
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
					return rid;
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

					if (! checkFilter(scope, table, fname, ffdef, val, rid, out).resume)
						return;

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

					if (! checkFilter(scope, table, fname, ffdef, val, rid, out).resume)
						return;
					
					recid = request.getInterface().prevPeerKey(did, ffdef.getIndexName(), table, fname, val, rid);
				}
				
				valb = request.getInterface().prevPeerKey(did, ffdef.getIndexName(), table, fname, val);
			}
		}
		catch (Exception x) {
			Logger.error("traverseIndex error: " + x);
		}
	}

	public ExpressionResult checkFilter(IVariableAware scope, String table, String fname, DbField ffdef, Object val, Object rid, IFilter out) throws DatabaseException, OperatingContextException {
		String did = this.request.getTenant();

		if (ffdef.isScalar()) {
			ExpressionResult filterResult = out.check(this, scope, table, rid);

			if (! filterResult.resume)
				return filterResult;
		}
		else {
			byte[] recsid = request.getInterface().prevPeerKey(did, DB_GLOBAL_INDEX_SUB, table, fname, val, rid, null);

			while (recsid != null) {
				Object rsid = ByteUtil.extractValue(recsid);

				ExpressionResult filterResult = (out instanceof IFilterSubAware)
						? ((IFilterSubAware) out).check(this, scope, table, rid, rsid)
						: out.check(this, scope, table, rid);

				if (! filterResult.resume)
					return filterResult;

				recsid = request.getInterface().prevPeerKey(did, DB_GLOBAL_INDEX_SUB, table, fname, val, rsid);
			}
		}

		return ExpressionResult.ACCEPTED;
	}
	
	public void executeTrigger(String table, String id, String op, BaseStruct context) throws OperatingContextException {
		SchemaResource schema = ResourceHub.getResources().getSchema();
		List<DbTrigger> trigs = schema.getDbTriggers(table, op);
		
		for (DbTrigger trig : trigs) {
			String spname = trig.execute;
			
			try {
				Class<?> spclass = Class.forName(spname);				
				ITrigger sp = (ITrigger) spclass.newInstance();
				sp.execute(this, table, id, context);
			}
			catch (OperatingContextException x) {
				throw x;
			}
			catch (Exception x) {
				Logger.error("Unable to load/start trigger class: " + x);
			}
		}
	}

	// if even one passes then "can" and true
	public boolean executeCanTrigger(String table, String id, String op, BaseStruct context) throws OperatingContextException {
		SchemaResource schema = ResourceHub.getResources().getSchema();
		List<DbTrigger> trigs = schema.getDbTriggers(table, op);

		if (trigs.size() == 0)
			return true;

		for (DbTrigger trig : trigs) {
			String spname = trig.execute;

			try {
				Class<?> spclass = Class.forName(spname);
				ITrigger sp = (ITrigger) spclass.newInstance();
				if (sp.execute(this, table, id, context))
					return true;
			}
			catch (OperatingContextException x) {
				throw x;
			}
			catch (Exception x) {
				Logger.error("Unable to load/start trigger class: " + x);
			}
		}

		return false;
	}

	// if even one fails then "cannot" and true
	public boolean executeCannotTrigger(String table, String id, String op, BaseStruct context) throws OperatingContextException {
		SchemaResource schema = ResourceHub.getResources().getSchema();
		List<DbTrigger> trigs = schema.getDbTriggers(table, op);

		if (trigs.size() == 0)
			return false;

		for (DbTrigger trig : trigs) {
			String spname = trig.execute;

			try {
				Class<?> spclass = Class.forName(spname);
				ITrigger sp = (ITrigger) spclass.newInstance();
				if (! sp.execute(this, table, id, context))
					return true;
			}
			catch (OperatingContextException x) {
				throw x;
			}
			catch (Exception x) {
				Logger.error("Unable to load/start trigger class: " + x);
			}
		}

		return false;
	}
}
