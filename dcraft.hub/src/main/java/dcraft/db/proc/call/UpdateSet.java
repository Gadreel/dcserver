package dcraft.db.proc.call;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import dcraft.db.DbServiceRequest;
import dcraft.db.tables.TablesAdapter;
import dcraft.db.proc.IUpdatingStoredProc;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationMarker;
import dcraft.hub.op.OperationOutcomeStruct;
import dcraft.hub.time.BigDateTime;
import dcraft.log.Logger;
import dcraft.struct.ListStruct;
import dcraft.struct.RecordStruct;
import dcraft.struct.Struct;

public class UpdateSet implements IUpdatingStoredProc {
	@Override
	public void execute(DbServiceRequest request, OperationOutcomeStruct callback) throws OperatingContextException {
		RecordStruct params = request.getDataAsRecord();
		
		String table = params.getFieldAsString("Table");
		String field = params.getFieldAsString("Field");
		String op = params.getFieldAsString("Operation");
		
		ListStruct records = params.getFieldAsList("Records");
		ListStruct subids = params.getFieldAsList("Values");
		
		TablesAdapter db = TablesAdapter.of(request);
		
		BigDateTime when = BigDateTime.nowDateTime();		// TODO store in params for replication - use same when 
		
		try (OperationMarker om = OperationMarker.create()) {
			for (Struct ssid : records.items()) {
				String id = ssid.toString();
				
				// make a copy
				List<String> lsubids = subids.toStringList();
				List<String> othersubids = new ArrayList<>();
				
				db.traverseSubIds(table, id, field, when, false, new Function<Object, Boolean>() {
					@Override
					public Boolean apply(Object msub) {
						try {
							String suid = msub.toString();
							
							boolean fnd = lsubids.remove(suid);
							
							if (!fnd)
								othersubids.add(suid);
							
							if ("RemoveFromSet".equals(op) && fnd) {
								// if present in our list then retire it
								db.setFields(table, id, new RecordStruct()
										.with(field, new RecordStruct()
												.with(suid, new RecordStruct()
														.with("Retired", true)
												)
										)
								);
								
								return true;
							}
						}
						catch (Exception x) {
							Logger.error("Unable to remove value from set");
						}
						
						return false;
					}
				});
				
				// Make negates non matches, so retire those
				if ("MakeSet".equals(op)) {
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
				}
				
				// Make and Add will add any remaining - unmatched - suids
				if ("MakeSet".equals(op) || "AddToSet".equals(op)) {
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
				
				// TODO make a record of everything for replication? or just let it figure it out?
			}
		}
		catch (Exception x) {
			Logger.error("Unable to update set");
		}
		
		callback.returnEmpty();
	}
}
