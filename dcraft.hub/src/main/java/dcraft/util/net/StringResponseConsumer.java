package dcraft.util.net;

import dcraft.hub.op.OperationOutcome;
import dcraft.log.Logger;
import dcraft.struct.CompositeStruct;

import java.net.http.HttpResponse;
import java.util.function.BiConsumer;

public class StringResponseConsumer implements BiConsumer<HttpResponse<String>, Throwable> {
    static public StringResponseConsumer of(OperationOutcome<String> callback) {
        StringResponseConsumer consumer = new StringResponseConsumer();
        consumer.callback = callback;

        return consumer;
    }

    protected OperationOutcome<String> callback = null;

    @Override
    public void accept(HttpResponse<String> response, Throwable x) {
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
            System.out.println("got body: " + response.body());

            callback.returnEmpty();
        }
        else {
            callback.returnValue(response.body());
        }
    }
}
