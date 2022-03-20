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
import java.net.URLEncoder;
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

                    String contentType = "application/json";

                    RecordStruct headers = request.getFieldAsRecord("headers");

                    for (FieldStruct fld : headers.getFields()) {
                        if (fld.getValue() != null) {
                            builder.header(fld.getName(), fld.getValue().toString());

                            if ("Content-Type".equals(fld.getName()))
                                contentType = fld.getValue().toString();
                        }
                    }

                    System.out.println("Endpoint: " + endpoint);
                    System.out.println("Request: " + request.toPrettyString());

                    if (request.isNotFieldEmpty("body")) {
                        if ("application/x-www-form-urlencoded".equals(contentType)) {
                            RecordStruct params = request.getFieldAsRecord("body");     // expect a record struct

                            if (params != null) {
                                String paramstr = "";

                                for (FieldStruct fld : params.getFields()) {
                                    if (paramstr.length() > 0)
                                        paramstr += "&";

                                    paramstr += fld.getName() + "=" + URLEncoder.encode(fld.getValue().toString(), "UTF-8");
                                }

                                builder.POST(HttpRequest.BodyPublishers.ofString(paramstr));
                            }
                            else {
                                Logger.error("Unsupported form data type: " + contentType);
                                return NullValue.NULL;
                            }
                        }
                        else if ("application/json".equals(contentType)) {
                            String json = request.getFieldAsString("body");     // expect a composite struct

                            builder.POST(HttpRequest.BodyPublishers.ofString(json));
                        }
                        else {
                            Logger.error("Unsupported content type: " + contentType);
                            return NullValue.NULL;
                        }
                    }
                    else {
                        builder.GET();
                    }

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
                        respheaders.with(hdrs, response.headers().firstValue(hdrs).get());
                    }

                    // TODO support 'cookies' too?

                    System.out.println("response: " + funcresult.toPrettyString());

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
