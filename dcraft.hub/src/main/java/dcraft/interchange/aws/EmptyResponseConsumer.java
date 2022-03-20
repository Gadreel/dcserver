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

        System.out.println("code: " + response.statusCode());
        //System.out.println("got: " + response.body());

        // if there was an exception
        if (x != null) {
            Logger.error("Bad Response exception: " + x);     // must be an error so callback gets an error
            System.out.println("got: " + response);
        }
        else if (response.statusCode() >= 400) {
            Logger.error("Bad Response code: " + response.statusCode());     // must be an error so callback gets an error
            System.out.println("got: " + response);
        }

        callback.returnEmpty();
    }
}
