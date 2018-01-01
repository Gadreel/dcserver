package dcraft.db.proc.call;

import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

import dcraft.db.DbServiceRequest;
import dcraft.db.proc.IStoredProc;
import dcraft.db.tables.TablesAdapter;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationMarker;
import dcraft.hub.op.OperationOutcomeStruct;
import dcraft.hub.time.BigDateTime;
import dcraft.log.Logger;
import dcraft.struct.ListStruct;
import dcraft.struct.RecordStruct;
import dcraft.struct.Struct;

public class CountIndexes implements IStoredProc {
	@Override
	public void execute(DbServiceRequest request, OperationOutcomeStruct callback) throws OperatingContextException {
		RecordStruct params = request.getDataAsRecord();
		
		String table = params.getFieldAsString("Table");
		String fname = params.getFieldAsString("Field");
		BigDateTime when = params.getFieldAsBigDateTime("When");
		boolean historical = params.getFieldAsBooleanOrFalse("Historical");	
		ListStruct values = params.getFieldAsList("Values");
		
		if (when == null)
			when = BigDateTime.nowDateTime();
		
		TablesAdapter db = TablesAdapter.of(request);
		ListStruct out = ListStruct.list();
		
		try (OperationMarker om = OperationMarker.create()) {
			if ((values == null) || (values.size() == 0)) {
				BigDateTime fwhen = when;
				
				db.traverseIndexValRange(table, fname, null, null, fwhen, historical, new Function<Object,Boolean>() {
					@Override
					public Boolean apply(Object val) {
						try {
							AtomicLong cnt = new AtomicLong();
							
							db.traverseIndex(table, fname, val, fwhen, historical, new Function<Object,Boolean>() {
								@Override
								public Boolean apply(Object subid) {
									cnt.incrementAndGet();
									return true;
								}
							});
						
							out.with(RecordStruct.record()
									.with("Name", val)
									.with("Count", new Long(cnt.get()))
							);
							
							return true;
						}
						catch (Exception x) {
							Logger.error("Issue with counting index record: " + x);
						}
						
						return false;
					}
				});
			}
			else {
				for (Struct vs : values.items()) {
					Object val = Struct.objectToCore(vs);
					
					AtomicLong cnt = new AtomicLong();
			
					db.traverseIndex(table, fname, val, when, historical, new Function<Object,Boolean>() {				
						@Override
						public Boolean apply(Object subid) {
							cnt.incrementAndGet();
							return true;
						}
					});
					
					out.with(RecordStruct.record()
						.with("Name", val)
						.with("Count", new Long(cnt.get()))
					);
				}
			}
			
			if (! om.hasErrors()) {
				callback.returnValue(out);
				return;
			}
		}
		catch (Exception x) {
			Logger.error("Issue with counting index record: " + x);
		}
		
		callback.returnEmpty();
	}
}
