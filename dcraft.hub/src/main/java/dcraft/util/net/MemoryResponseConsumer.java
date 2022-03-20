package dcraft.util.net;

import dcraft.hub.op.OperationOutcome;
import dcraft.log.Logger;
import dcraft.util.Memory;

import java.net.http.HttpResponse;
import java.util.function.BiConsumer;

public class MemoryResponseConsumer implements BiConsumer<HttpResponse<Memory>, Throwable> {
    static public MemoryResponseConsumer of(OperationOutcome<Memory> callback) {
        MemoryResponseConsumer consumer = new MemoryResponseConsumer();
        consumer.callback = callback;

        return consumer;
    }

    protected OperationOutcome<Memory> callback = null;

    @Override
    public void accept(HttpResponse<Memory> response, Throwable x) {
        callback.useContext();		// restore context

        System.out.println("code: " + response.statusCode());
        //System.out.println("got: " + response.body());

        // if there was an exception
        if (x != null) {
            Logger.error("Bad Response exception: " + x);     // must be an error so callback gets an error
            System.out.println("got: " + response);

            callback.returnEmpty();
        }
        else if (response.statusCode() >= 400) {
            Logger.error("Bad Response code: " + response.statusCode());     // must be an error so callback gets an error
            System.out.println("got: " + response);

            callback.returnEmpty();
        }
        else {
            callback.returnValue(response.body());
        }
    }
}
