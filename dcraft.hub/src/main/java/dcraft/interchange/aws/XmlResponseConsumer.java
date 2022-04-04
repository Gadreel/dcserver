package dcraft.interchange.aws;

import dcraft.hub.op.OperationOutcome;
import dcraft.log.Logger;
import dcraft.xml.XElement;
import dcraft.xml.XmlReader;

import java.net.http.HttpResponse;
import java.util.function.BiConsumer;

public class XmlResponseConsumer implements BiConsumer<HttpResponse<String>, Throwable> {
    static public XmlResponseConsumer of(OperationOutcome<XElement> callback) {
        XmlResponseConsumer consumer = new XmlResponseConsumer();
        consumer.callback = callback;

        return consumer;
    }

    protected OperationOutcome<XElement> callback = null;

    @Override
    public void accept(HttpResponse<String> response, Throwable x) {
        callback.useContext();		// restore context

        if ((response == null) ? AWSUtilCore.checkResponse(x, -1, null) : AWSUtilCore.checkResponse(x, response.statusCode(), response.headers()))
            callback.returnValue(XmlReader.parse(response.body(), true, true));
        else
            callback.returnEmpty();
    }
}
