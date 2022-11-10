package dcraft.interchange.aws;

import dcraft.filestore.CommonPath;
import dcraft.hub.ResourceHub;
import dcraft.hub.app.ApplicationHub;
import dcraft.hub.op.*;
import dcraft.log.Logger;
import dcraft.struct.CompositeStruct;
import dcraft.struct.RecordStruct;
import dcraft.struct.builder.BuilderState;
import dcraft.struct.builder.BuilderStateException;
import dcraft.util.HashUtil;
import dcraft.util.Memory;
import dcraft.util.StringUtil;
import dcraft.xml.XElement;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class AWSSMS {
    /* useful links
        https://docs.aws.amazon.com/ses/latest/APIReference-V2/API_SendEmail.html#API_SendEmail_RequestBody
     */
    static public void sendEmail(String alt, RecordStruct emailInfo, OperationOutcomeComposite callback) {
        XElement connection = ApplicationHub.getCatalogSettings("Interchange-Aws", alt);

        String region = connection.getAttribute("EmailRegion", connection.getAttribute("Region"));

        try {
            AWSSMS.sendEmail(connection, region, emailInfo.toMemory(), callback);
        }
        catch (BuilderStateException x) {
            Logger.error("Unable to stringify the composite object: " + x);
            callback.returnEmpty();
        }
    }

    static public void sendEmail(String alt, Memory body, OperationOutcomeComposite callback) {
        XElement connection = ApplicationHub.getCatalogSettings("Interchange-Aws", alt);

        String region = connection.getAttribute("EmailRegion", connection.getAttribute("Region"));

        AWSSMS.sendEmail(connection, region, body, callback);
    }

    /* useful links
        https://docs.aws.amazon.com/ses/latest/APIReference-V2/API_SendEmail.html#API_SendEmail_RequestBody
        for the body handling see
        https://docs.aws.amazon.com/ses/latest/dg/send-email-raw.html
        and also for raw see notes in here
        https://docs.aws.amazon.com/ses/latest/APIReference/API_SendRawEmail.html
     */
    static public void sendEmail(XElement connection, String region, Memory body, OperationOutcomeComposite callback) {
        body.setPosition(0);

        String payload_hash = HashUtil.getSha256(body.getInputStream());
        body.setPosition(0);

        HttpClient httpClient = HttpClient.newHttpClient();
        HttpRequest.Builder req = AWSUtilCore.buildRequest(connection, AWSSMS.buildHostOptionSMS(region, "/v2/email/outbound-emails")
                .with("Method", "POST")
                .with("PayloadHash", payload_hash)
             )
            .POST(HttpRequest.BodyPublishers.ofByteArray(body.toArray()));      // TODO try inputstream instead?

        req.header("ContentType", "application/json; charset=UTF-8;");

        httpClient
                .sendAsync(req.build(), HttpResponse.BodyHandlers.ofString())
                .whenComplete(JsonResponseConsumer.of(callback));
    }

    static public RecordStruct buildHostOptionSMS(String region, String path) {
        RecordStruct options = AWSSMS.buildHostOptionSMS(region);

        options
                .with("Path", path);  // .substring(1));

        return options;
    }

    static public RecordStruct buildHostOptionSMS(String region) {
        String host = "email.amazonaws.com";

        if (StringUtil.isNotEmpty(region)) {
            host = "email." + region + ".amazonaws.com";

            //if (AWSUtilCore.isHostDualStack(region))
            //    host = "api.ec2." + region + ".aws";
        }

        return RecordStruct.record()
                .with("Region", region)
                .with("Service", "ses")
                .with("Host", host);
    }
}
