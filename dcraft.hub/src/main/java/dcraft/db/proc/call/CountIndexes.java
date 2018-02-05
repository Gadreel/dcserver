package dcraft.db.proc.call;

import dcraft.db.proc.BasicFilter;
import dcraft.db.proc.ExpressionResult;
import dcraft.db.proc.IStoredProc;
import dcraft.db.proc.filter.Max;
import dcraft.db.ICallContext;
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
	public void execute(ICallContext request, OperationOutcomeStruct callback) throws OperatingContextException {
		RecordStruct params = request.getDataAsRecord();
		
		String table = params.getFieldAsString("Table");
		String fname = params.getFieldAsString("Field");
		BigDateTime when = params.getFieldAsBigDateTime("When");
		boolean historical = params.getFieldAsBooleanOrFalse("Historical");	
		ListStruct values = params.getFieldAsList("Values");
		
		TablesAdapter db = TablesAdapter.of(request, when, historical);
		ListStruct out = ListStruct.list();
		
		try (OperationMarker om = OperationMarker.create()) {
			if ((values == null) || (values.size() == 0)) {
				db.traverseIndexValRange(table, fname, null, null, new BasicFilter() {
					@Override
					public ExpressionResult check(TablesAdapter adapter, Object val) throws OperatingContextException {
						Max cnt = Max.max();
						
						db.traverseIndex(table, fname, val, cnt);
						
						out.with(RecordStruct.record()
								.with("Name", val)
								.with("Count", cnt.getCount())
						);
						
						return ExpressionResult.accepted();
					}
				});
			}
			else {
				for (Struct vs : values.items()) {
					Object val = Struct.objectToCore(vs);
					
					Max cnt = Max.max();
			
					db.traverseIndex(table, fname, val, cnt);
					
					out.with(RecordStruct.record()
						.with("Name", val)
						.with("Count", cnt.getCount())
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
