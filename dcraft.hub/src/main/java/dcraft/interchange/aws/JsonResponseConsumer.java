package dcraft.interchange.aws;

import dcraft.hub.op.OperationOutcome;
import dcraft.struct.CompositeStruct;
import dcraft.struct.Struct;
import dcraft.xml.XElement;
import dcraft.xml.XmlReader;

import java.net.http.HttpResponse;
import java.util.function.BiConsumer;

public class JsonResponseConsumer implements BiConsumer<HttpResponse<String>, Throwable> {
    static public JsonResponseConsumer of(OperationOutcome<CompositeStruct> callback) {
        JsonResponseConsumer consumer = new JsonResponseConsumer();
        consumer.callback = callback;

        return consumer;
    }

    protected OperationOutcome<CompositeStruct> callback = null;

    @Override
    public void accept(HttpResponse<String> response, Throwable x) {
        callback.useContext();		// restore context

        if ((response == null) ? AWSUtilCore.checkResponse(x, -1, null) : AWSUtilCore.checkResponse(x, response.statusCode(), response.headers())) {
            callback.returnValue(Struct.objectToComposite(response.body()));
        }
        else {
            if (response != null)
                System.out.println("error body: " + response.body());

            callback.returnEmpty();
        }
    }
}
