package dcraft.interchange.aws;

import dcraft.hub.op.OperationOutcome;
import dcraft.log.Logger;
import dcraft.util.Memory;

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
            callback.returnValue(new Memory(response.body()));
        }
    }
}
