package dcraft.interchange.aws;

import dcraft.hub.op.OperationOutcomeEmpty;
import dcraft.hub.op.OperationOutcomeRecord;
import dcraft.log.Logger;
import dcraft.struct.RecordStruct;
import dcraft.util.StringUtil;
import dcraft.xml.XmlReader;

import java.net.http.HttpResponse;
import java.util.function.BiConsumer;

public class HeadResponseConsumer implements BiConsumer<HttpResponse<Void>, Throwable> {
    static public HeadResponseConsumer of(OperationOutcomeRecord callback) {
        HeadResponseConsumer consumer = new HeadResponseConsumer();
        consumer.callback = callback;

        return consumer;
    }

    protected OperationOutcomeRecord callback = null;

    @Override
    public void accept(HttpResponse<Void> response, Throwable x) {
        callback.useContext();		// restore context

        if ((response == null) ? AWSUtilCore.checkResponse(x, -1, null) : AWSUtilCore.checkResponse(x, response.statusCode(), response.headers())) {
            RecordStruct headers = RecordStruct.record();

            for (String header : response.headers().map().keySet()) {
                headers.with(header, StringUtil.join(response.headers().allValues(header), "|"));
            }

            callback.returnValue(headers);
        }
        else {
            callback.returnEmpty();
        }
    }
}
