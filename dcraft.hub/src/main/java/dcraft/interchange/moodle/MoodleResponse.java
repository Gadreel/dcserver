package dcraft.interchange.moodle;


import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationOutcomeComposite;
import dcraft.hub.op.OperationOutcomeRecord;
import dcraft.log.Logger;
import dcraft.struct.CompositeParser;
import dcraft.struct.CompositeStruct;
import dcraft.util.StringUtil;
import dcraft.util.cb.TimeoutPlan;

import java.net.http.HttpResponse;
import java.util.function.Consumer;

abstract public class MoodleResponse extends OperationOutcomeComposite implements Consumer<HttpResponse<String>> {
    public MoodleResponse() throws OperatingContextException {
        super();
    }

    public MoodleResponse(TimeoutPlan plan) throws OperatingContextException {
        super(plan);
    }

    @Override
    public void accept(HttpResponse<String> response) {
        this.useContext();

        int responseCode = response.statusCode();
        CompositeStruct respBody = null;

        if ((responseCode < 200) || (responseCode > 299)) {
            Logger.error("Error processing request: Moodle sent an error response code: " + responseCode);
        }
        else {
            String respraw = response.body();

            if (StringUtil.isNotEmpty(respraw)) {
                respBody = CompositeParser.parseJson(respraw);

                if (respBody == null) {
                    Logger.error("Error processing request: Moodle sent an incomplete response: " + responseCode);
                }
                else {
                    System.out.println("Moodle Resp: " + responseCode + "\n" + respBody.toPrettyString());

                    /*
                    if (!"Ok".equals(respBody.selectAsString("messages.resultCode"))) {
                        Logger.error("Error processing auth request: " + responseCode);
                    }
                     */
                }
            }
        }

        this.returnValue(respBody);
    }
}