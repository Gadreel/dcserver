package dcraft.interchange.aws;

import dcraft.hub.op.OperationOutcome;
import dcraft.log.Logger;
import dcraft.util.Memory;
import dcraft.xml.XmlReader;

import java.net.http.HttpResponse;
import java.util.function.BiConsumer;

public class BinaryResponseConsumer implements BiConsumer<HttpResponse<byte[]>, Throwable> {
    static public BinaryResponseConsumer of(OperationOutcome<Memory> callback) {
        BinaryResponseConsumer consumer = new BinaryResponseConsumer();
        consumer.callback = callback;

        return consumer;
    }

    protected OperationOutcome<Memory> callback = null;

    @Override
    public void accept(HttpResponse<byte[]> response, Throwable x) {
        callback.useContext();		// restore context

        if ((response == null) ? AWSUtilCore.checkResponse(x, -1, null) : AWSUtilCore.checkResponse(x, response.statusCode(), response.headers()))
            callback.returnValue(new Memory(response.body()));
        else
            callback.returnEmpty();
    }
}
