package dcraft.cms.store.db.discounts;

import dcraft.cms.store.db.Util;
import dcraft.db.ICallContext;
import dcraft.db.proc.IStoredProc;
import dcraft.db.tables.TableUtil;
import dcraft.db.tables.TablesAdapter;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationOutcomeStruct;
import dcraft.struct.RecordStruct;

public class Revive implements IStoredProc {
	@Override
	public void execute(ICallContext request, OperationOutcomeStruct callback) throws OperatingContextException {
		RecordStruct data = request.getDataAsRecord();

		TablesAdapter db = TablesAdapter.of(request);

		String id = data.getFieldAsString("Id");

		TableUtil.reviveRecord(db, "dcmDiscount", id);

		db.updateScalar("dcmDiscount", id, "dcmState", "Check");

		Util.resolveDiscountRules(db);

		callback.returnEmpty();
	}
}
