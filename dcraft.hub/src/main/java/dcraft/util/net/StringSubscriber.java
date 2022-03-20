package dcraft.util.net;

import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
import dcraft.struct.CompositeStruct;
import dcraft.xml.XmlReader;

import java.nio.charset.StandardCharsets;

public class StringSubscriber extends ByteArraySubscriber<String> {
    public StringSubscriber() throws OperatingContextException {
        super();
    }

    public StringSubscriber(OperationContext ctx) {
        super(ctx);
    }

    @Override
    public String transform(byte[] bytes) throws OperatingContextException {
        String body = new String(bytes, StandardCharsets.UTF_8);

        //System.out.println("Resp: " + resp.toPrettyString());

        return body;
    }
}
