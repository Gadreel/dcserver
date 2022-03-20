package dcraft.util.net;

import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
import dcraft.struct.CompositeParser;
import dcraft.struct.CompositeStruct;
import dcraft.struct.Struct;
import dcraft.xml.XElement;
import dcraft.xml.XmlReader;

import java.nio.charset.StandardCharsets;

public class XmlSubscriber extends ByteArraySubscriber<XElement> {
    public XmlSubscriber() throws OperatingContextException {
        super();
    }

    public XmlSubscriber(OperationContext ctx) {
        super(ctx);
    }

    @Override
    public XElement transform(byte[] bytes) throws OperatingContextException {
        String body = new String(bytes, StandardCharsets.UTF_8);

        XElement resp = XmlReader.parse(body, false, true);

        //System.out.println("Resp: " + resp.toPrettyString());

        return resp;
    }
}
