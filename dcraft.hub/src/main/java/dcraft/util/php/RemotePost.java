package dcraft.util.php;

import com.caucho.quercus.env.Env;
import com.caucho.quercus.env.NullValue;
import com.caucho.quercus.env.StringValue;
import com.caucho.quercus.env.Value;
import com.caucho.quercus.function.AbstractFunction;
import dcraft.hub.op.OperatingContextException;
import dcraft.interchange.authorize.AuthUtil;
import dcraft.log.Logger;
import dcraft.script.StackUtil;
import dcraft.struct.*;
import dcraft.task.IParentAwareWork;
import dcraft.util.StringUtil;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class RemotePost extends AbstractFunction {
    public RemotePost(){
    }

    @Override
    public Value call(Env env, Value []args) {
        if (args.length > 1) {
            RecordStruct request = Struct.objectToRecord(PhpUtil.valueToStruct(env, args[1]));

            if ((request != null) && (args[0] != null) && args[0].isString()) {
                try {
                    String endpoint = args[0].toString();

                    HttpRequest.Builder builder = HttpRequest.newBuilder()
                            .header("User-Agent", "dcServer/2021.1 (Language=Java/11)")
                            .uri(URI.create(endpoint));

                    RecordStruct headers = request.getFieldAsRecord("headers");

                    for (FieldStruct fld : headers.getFields()) {
                        if (fld.getValue() != null) {
                            builder.header(fld.getName(), fld.getValue().toString());
                        }
                    }

                    String json = request.getFieldAsString("body");

                    builder.POST(HttpRequest.BodyPublishers.ofString(json));

                    HttpResponse<String> response = HttpClient.newHttpClient().send(builder.build(), HttpResponse.BodyHandlers.ofString());

                    RecordStruct funcresult = RecordStruct.record();

                    int responseCode = response.statusCode();

                    funcresult.with("response", RecordStruct.record()
                            .with("code", responseCode)
                    );

                    String respraw = response.body();

                    funcresult.with("body", respraw);

                    RecordStruct respheaders = RecordStruct.record();

                    for (String hdrs : response.headers().map().keySet()) {
                        respheaders.with(hdrs, response.headers().firstValue(hdrs));
                    }

                    // TODO support 'cookies' too?

                    return PhpUtil.structToValue(env, funcresult);
                }
                catch (IOException x) {
                    Logger.warn("IO Exception for remote post: " + x);
                }
                catch (InterruptedException x) {
                    Logger.warn("Interrupt Exception for remote post: " + x);
                }
            }
            else {
                Logger.warn("Invalid arguments for remote post");
            }
        }
        else {
            Logger.warn("Missing arguments for remote post");
        }

        return NullValue.NULL;
    }
}
