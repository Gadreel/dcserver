package dcraft.cms.store.db.orders;

import dcraft.cms.store.OrderUtil;
import dcraft.db.ICallContext;
import dcraft.db.proc.IStoredProc;
import dcraft.db.tables.TablesAdapter;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationOutcomeStruct;
import dcraft.log.Logger;
import dcraft.struct.ListStruct;
import dcraft.struct.RecordStruct;
import dcraft.struct.Struct;

import java.math.BigDecimal;

public class Calculate implements IStoredProc {
	@Override
	public void execute(ICallContext request, OperationOutcomeStruct callback) throws OperatingContextException {
		RecordStruct data = request.getDataAsRecord();

		data.removeField("_ForTenant");
		data.removeField("_Database");

		TablesAdapter db = TablesAdapter.ofNow(request);

		RecordStruct order = OrderUtil.santitizeAndCalculateOrder(request, db, data);

		if (order == null) {
			callback.returnEmpty();
		}
		else {
			callback.returnValue(order);
		}
	}
}
