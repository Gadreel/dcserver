package dcraft.util.net;

import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
import dcraft.log.Logger;
import dcraft.struct.CompositeParser;
import dcraft.struct.CompositeStruct;

import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

public class JSONSubscriber extends ByteArraySubscriber<CompositeStruct> {
    public JSONSubscriber() throws OperatingContextException {
        super();
    }

    public JSONSubscriber(OperationContext ctx) {
        super(ctx);
    }

    @Override
    public CompositeStruct transform(byte[] bytes) throws OperatingContextException {
        String body = new String(bytes, StandardCharsets.UTF_8);

        return CompositeParser.parseJson(body);
    }
}
