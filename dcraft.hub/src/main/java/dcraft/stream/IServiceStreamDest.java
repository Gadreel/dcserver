package dcraft.stream;

import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationOutcomeStruct;

public interface IServiceStreamDest<T> extends IStreamDown<T> {
    void start() throws OperatingContextException;
    void end(OperationOutcomeStruct outcome) throws OperatingContextException;
}
