package dcraft.db.proc.call;

import dcraft.db.ICallContext;
import dcraft.db.proc.IUpdatingStoredProc;
import dcraft.db.tables.TableUtil;
import dcraft.db.tables.TablesAdapter;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationOutcomeStruct;
import dcraft.struct.ListStruct;
import dcraft.struct.RecordStruct;

public class DeleteRecord implements IUpdatingStoredProc {
	@Override
	public void execute(ICallContext request, OperationOutcomeStruct callback) throws OperatingContextException {
		RecordStruct params = request.getDataAsRecord();
		
		String table = params.getFieldAsString("Table");
		ListStruct ids = params.getFieldAsList("Ids");

		TablesAdapter db = TablesAdapter.ofNow(request);

		for (int i = 0; i < ids.size(); i++)
			db.deleteRecord(table, ids.getItemAsString(i));

		callback.returnEmpty();
	}
}
