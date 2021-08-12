package dcraft.cms.store.db.orders;

import dcraft.cms.store.OrderUtil;
import dcraft.db.ICallContext;
import dcraft.db.proc.IStoredProc;
import dcraft.db.tables.TablesAdapter;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationOutcomeStruct;
import dcraft.struct.RecordStruct;

public class Submit implements IStoredProc {
	@Override
	public void execute(ICallContext request, OperationOutcomeStruct callback) throws OperatingContextException {
		RecordStruct data = request.getDataAsRecord();

		data.removeField("_ForTenant");
		data.removeField("_Database");

		TablesAdapter db = TablesAdapter.of(request);

		// TODO add/support Captcha - require if configured

		OrderUtil.processAuthOrder(request, db, data, callback);
	}
}
