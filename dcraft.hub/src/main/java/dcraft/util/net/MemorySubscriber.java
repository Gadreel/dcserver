package dcraft.util.net;

import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
import dcraft.util.Memory;
import dcraft.xml.XElement;
import dcraft.xml.XmlReader;

import java.nio.charset.StandardCharsets;

public class MemorySubscriber extends ByteArraySubscriber<Memory> {
    public MemorySubscriber() throws OperatingContextException {
        super();
    }

    public MemorySubscriber(OperationContext ctx) {
        super(ctx);
    }

    @Override
    public Memory transform(byte[] bytes) throws OperatingContextException {
        Memory mem = new Memory(bytes);
        mem.setPosition(0);     // caller expects to start from 0
        return mem;
    }
}
