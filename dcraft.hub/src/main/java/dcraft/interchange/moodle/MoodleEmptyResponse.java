package dcraft.interchange.moodle;


import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationOutcomeComposite;
import dcraft.hub.op.OperationOutcomeEmpty;
import dcraft.log.Logger;
import dcraft.struct.CompositeParser;
import dcraft.struct.CompositeStruct;
import dcraft.util.StringUtil;
import dcraft.util.cb.TimeoutPlan;

import java.net.http.HttpResponse;
import java.util.function.Consumer;

abstract public class MoodleEmptyResponse extends OperationOutcomeEmpty implements Consumer<HttpResponse<String>> {
    public MoodleEmptyResponse() throws OperatingContextException {
        super();
    }

    public MoodleEmptyResponse(TimeoutPlan plan) throws OperatingContextException {
        super(plan);
    }

    @Override
    public void accept(HttpResponse<String> response) {
        this.useContext();

        int responseCode = response.statusCode();

        if ((responseCode < 200) || (responseCode > 299)) {
            Logger.error("Error processing request: Moodle sent an error response code: " + responseCode);
        }

        this.returnEmpty();
    }
}