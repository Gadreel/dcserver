package dcraft.cms.thread.db.trigger;

import dcraft.db.proc.ITrigger;
import dcraft.db.tables.TablesAdapter;
import dcraft.hub.op.OperatingContextException;
import dcraft.struct.Struct;

public class BlockExpire implements ITrigger {
    @Override
    public boolean execute(TablesAdapter db, String table, String id, Struct context) throws OperatingContextException {
        return false;
    }
}
