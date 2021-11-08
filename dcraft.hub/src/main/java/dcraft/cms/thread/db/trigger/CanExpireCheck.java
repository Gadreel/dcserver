package dcraft.cms.thread.db.trigger;

import dcraft.db.proc.ITrigger;
import dcraft.db.tables.TablesAdapter;
import dcraft.hub.op.OperatingContextException;
import dcraft.struct.BaseStruct;
import dcraft.struct.Struct;

public class CanExpireCheck implements ITrigger {
    @Override
    public boolean execute(TablesAdapter db, String table, String id, BaseStruct context) throws OperatingContextException {
        String type = Struct.objectToString(db.getScalar(table, id, "dcmMessageType"));

        return "ApproveUser".equals(type) || "RecoverUser".equals(type) || "dcmOrderPayment".equals(type);
    }
}
