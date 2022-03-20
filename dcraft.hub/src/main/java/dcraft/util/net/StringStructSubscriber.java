package dcraft.util.net;

import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
import dcraft.struct.scalar.StringStruct;

import java.nio.charset.StandardCharsets;

public class StringStructSubscriber extends ByteArraySubscriber<StringStruct> {
    public StringStructSubscriber() throws OperatingContextException {
        super();
    }

    public StringStructSubscriber(OperationContext ctx) {
        super(ctx);
    }

    @Override
    public StringStruct transform(byte[] bytes) throws OperatingContextException {
        String body = new String(bytes, StandardCharsets.UTF_8);

        //System.out.println("Resp: " + resp.toPrettyString());

        return StringStruct.of(body);
    }
}
