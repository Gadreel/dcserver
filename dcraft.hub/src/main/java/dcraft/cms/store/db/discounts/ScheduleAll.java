package dcraft.cms.store.db.discounts;

import dcraft.cms.store.db.Util;
import dcraft.db.ICallContext;
import dcraft.db.proc.IStoredProc;
import dcraft.db.tables.TablesAdapter;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationOutcomeStruct;

public class ScheduleAll implements IStoredProc {
    @Override
    public void execute(ICallContext request, OperationOutcomeStruct callback) throws OperatingContextException {
        TablesAdapter db = TablesAdapter.ofNow(request);

        Util.scheduleDiscountRules(db);

        callback.returnEmpty();
    }
}
