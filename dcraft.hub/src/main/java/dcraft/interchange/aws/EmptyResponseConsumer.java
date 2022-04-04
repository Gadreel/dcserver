package dcraft.interchange.aws;

import dcraft.hub.op.OperationOutcome;
import dcraft.hub.op.OperationOutcomeEmpty;
import dcraft.log.Logger;
import dcraft.util.Memory;

import java.net.http.HttpResponse;
import java.util.function.BiConsumer;

public class EmptyResponseConsumer implements BiConsumer<HttpResponse<Void>, Throwable> {
    static public EmptyResponseConsumer of(OperationOutcomeEmpty callback) {
        EmptyResponseConsumer consumer = new EmptyResponseConsumer();
        consumer.callback = callback;

        return consumer;
    }

    protected OperationOutcomeEmpty callback = null;

    @Override
    public void accept(HttpResponse<Void> response, Throwable x) {
        callback.useContext();		// restore context

        if ((response == null) ? AWSUtilCore.checkResponse(x, -1, null) : AWSUtilCore.checkResponse(x, response.statusCode(), response.headers()))
            callback.returnEmpty();
        else
            callback.returnEmpty();
    }
}
