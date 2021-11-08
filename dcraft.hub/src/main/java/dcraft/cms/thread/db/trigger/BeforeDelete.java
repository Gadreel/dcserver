package dcraft.cms.thread.db.trigger;

import dcraft.cms.thread.db.ThreadUtil;
import dcraft.db.proc.ITrigger;
import dcraft.db.tables.TablesAdapter;
import dcraft.hub.op.OperatingContextException;
import dcraft.struct.BaseStruct;
import dcraft.struct.Struct;

public class BeforeDelete implements ITrigger {
    @Override
    public boolean execute(TablesAdapter db, String table, String id, BaseStruct context) throws OperatingContextException {
        ThreadUtil.clearThreadIndex(db, id);
        return true;
    }
}
