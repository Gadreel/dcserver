package dcraft.db.proc.call;

import dcraft.db.ICallContext;
import dcraft.db.tables.TablesAdapter;
import dcraft.db.proc.IUpdatingStoredProc;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationOutcomeStruct;
import dcraft.struct.RecordStruct;

public class RetireRecord implements IUpdatingStoredProc {
	@Override
	public void execute(ICallContext request, OperationOutcomeStruct callback) throws OperatingContextException {
		RecordStruct params = request.getDataAsRecord();
		
		String table = params.getFieldAsString("Table");
		String id = params.getFieldAsString("Id");
		
		// TODO add db filter option
		//d runFilter("Retire") quit:Errors  ; if any violations in filter then do not proceed
		
		TablesAdapter db = TablesAdapter.ofNow(request);

		db.setStaticScalar(table, id, "Retired", true);
		
		callback.returnEmpty();
	}
}
