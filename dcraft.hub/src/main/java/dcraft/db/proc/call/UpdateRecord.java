package dcraft.db.proc.call;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import dcraft.db.ICallContext;
import dcraft.db.tables.TableUtil;
import dcraft.db.tables.TablesAdapter;
import dcraft.db.proc.IUpdatingStoredProc;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationOutcomeStruct;
import dcraft.hub.time.BigDateTime;
import dcraft.struct.ListStruct;
import dcraft.struct.RecordStruct;
import dcraft.struct.Struct;
import dcraft.util.StringUtil;

public class UpdateRecord implements IUpdatingStoredProc {
	@Override
	public void execute(ICallContext request, OperationOutcomeStruct callback) throws OperatingContextException {

		// TODO switch to
		// TableUtil.updateRecord();

		boolean isUpdate = request.getOp().equals("dcUpdateRecord");

		RecordStruct params = request.getDataAsRecord();
		String table = params.getFieldAsString("Table");
		
		// ===========================================
		//  verify the fields
		// ===========================================
		
		RecordStruct fields = params.getFieldAsRecord("Fields");
		BigDateTime when = params.getFieldAsBigDateTime("When");
		
		TablesAdapter db = TablesAdapter.of(request, when, false);
		
		if (! request.isReplicating()) {
			// only check first time, otherwise allow replication
			if (! db.checkFieldsInternal(table, fields, params.getFieldAsString("Id"))) {
				callback.returnEmpty();
				return;
			}
		}
		
		// it is possible for Id to be set by trigger (e.g. with domains)
		String id = params.getFieldAsString("Id");
		
		// ===========================================
		//  run before trigger
		// ===========================================
		db.executeTrigger(table, id, isUpdate ? "BeforeUpdate" : "BeforeInsert", fields);
		
		// TODO maybe check for errors here?
		
		// TODO add db filter option
		//d runFilter("Insert" or "Update") quit:Errors  ; if any violations in filter then do not proceed
		
		// ===========================================
		//  create new id
		// ===========================================
		
		// don't create a new id during replication - not even for dcInsertRecord
		if (StringUtil.isEmpty(id)) {
			id = db.createRecord(table);
			
			if (StringUtil.isEmpty(id)) {
				callback.returnEmpty();
				return;
			}
			
			params.with("Id", id);
		}

		// ===========================================
		//  do the data update
		// ===========================================
		db.setFieldsInternal(table, id, fields);
		
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
				ListStruct rsubitds = rset.getFieldAsList("Values");

				List<String> lsubids = (rsubitds != null) ? rsubitds.toStringList() : new ArrayList<>();
				List<String> othersubids = new ArrayList<>();
				
				db.traverseSubIds(table, id, field, new Function<Object,Boolean>() {
					@Override
					public Boolean apply(Object msub) {
						String suid = msub.toString();
					
						// if a value is already set, don't set it again
						if (!lsubids.remove(suid))
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
		
		// ===========================================
		//  return results
		// ===========================================
		
		// don't bother returning data during replication 
		if (! isUpdate && ! request.isReplicating()) {
			callback.returnValue(RecordStruct.record()
				.with("Id", id));
			
			return;
		}
		
		callback.returnEmpty();
	}
}
