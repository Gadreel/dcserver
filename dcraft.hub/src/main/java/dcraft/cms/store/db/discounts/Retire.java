package dcraft.cms.store.db.discounts;

import dcraft.cms.store.db.Util;
import dcraft.db.ICallContext;
import dcraft.db.proc.IStoredProc;
import dcraft.db.tables.TableUtil;
import dcraft.db.tables.TablesAdapter;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationOutcomeStruct;
import dcraft.struct.RecordStruct;

public class Retire implements IStoredProc {
	@Override
	public void execute(ICallContext request, OperationOutcomeStruct callback) throws OperatingContextException {
		RecordStruct data = request.getDataAsRecord();

		TablesAdapter db = TablesAdapter.of(request);

		String id = data.getFieldAsString("Id");

		db.updateScalar("dcmDiscount", id, "dcmState", "Check");
		db.updateScalar("dcmDiscount", id, "dcmActive", false);

		Util.resolveDiscountRules(db);

		TableUtil.retireRecord(db, "dcmDiscount", id);

		callback.returnEmpty();
	}
}
