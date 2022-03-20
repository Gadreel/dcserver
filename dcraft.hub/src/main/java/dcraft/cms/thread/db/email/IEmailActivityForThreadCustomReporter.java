package dcraft.cms.thread.db.email;

import dcraft.hub.op.OperatingContextException;
import dcraft.struct.RecordStruct;

public interface IEmailActivityForThreadCustomReporter {
    // return true if you want the caller to automatically report to sentinel that this is processed
    boolean reportReceived(String actid, String auditkey, RecordStruct reportData, RecordStruct handlerData) throws OperatingContextException;
}
