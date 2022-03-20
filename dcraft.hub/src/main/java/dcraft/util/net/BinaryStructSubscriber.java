package dcraft.util.net;

import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
import dcraft.struct.scalar.BinaryStruct;
import dcraft.util.Memory;

public class BinaryStructSubscriber extends ByteArraySubscriber<BinaryStruct> {
    public BinaryStructSubscriber() throws OperatingContextException {
        super();
    }

    public BinaryStructSubscriber(OperationContext ctx) {
        super(ctx);
    }

    @Override
    public BinaryStruct transform(byte[] bytes) throws OperatingContextException {
        Memory mem = new Memory(bytes);
        mem.setPosition(0);     // caller expects to start from 0
        return BinaryStruct.of(mem);
    }
}
