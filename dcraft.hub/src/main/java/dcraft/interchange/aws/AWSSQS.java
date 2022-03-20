package dcraft.interchange.aws;

import dcraft.hub.op.OperationOutcome;
import dcraft.log.Logger;
import dcraft.service.portable.PortableMessageUtil;
import dcraft.struct.ListStruct;
import dcraft.struct.RecordStruct;
import dcraft.util.StringUtil;
import dcraft.xml.XElement;
import dcraft.xml.XmlReader;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.List;
import java.util.function.BiConsumer;

public class AWSSQS {
    static public void listQueues(XElement connection, String region, OperationOutcome<XElement> callback) {
        RecordStruct params = RecordStruct.record()
                .with("Action", "ListQueues");

        HttpClient httpClient = HttpClient.newHttpClient();
        HttpRequest.Builder req = AWSUtilCore.buildRequest(connection, AWSSQS.buildHostOptionSQS(region, params));

        httpClient
                .sendAsync(req.build(), HttpResponse.BodyHandlers.ofString())
                .whenComplete(XmlResponseConsumer.of(callback));
    }

    // queue should be formatted like "/333517786763/EmailUpdate/"
    // max number is 10
    static public void getMessages(XElement connection, String region, String queue, int number, OperationOutcome<XElement> callback) {
        RecordStruct params = RecordStruct.record()
                .with("Action", "ReceiveMessage")
                .with("MaxNumberOfMessages", number)
                .with("AttributeName", "All");

        HttpClient httpClient = HttpClient.newHttpClient();
        HttpRequest.Builder req = AWSUtilCore.buildRequest(connection, AWSSQS.buildHostOptionSQS(region, params)
                .with("Path", queue)
        );

        httpClient
                .sendAsync(req.build(), HttpResponse.BodyHandlers.ofString())
                .whenComplete(XmlResponseConsumer.of(callback));
    }

    // queue should be formatted like "/333517786763/EmailUpdate/"
    static public void deleteMessage(XElement connection, String region, String queue, String handle, OperationOutcome<XElement> callback) {
        RecordStruct params = RecordStruct.record()
                .with("Action", "DeleteMessage")
                .with("ReceiptHandle", handle);

        HttpClient httpClient = HttpClient.newHttpClient();
        HttpRequest.Builder req = AWSUtilCore.buildRequest(connection, AWSSQS.buildHostOptionSQS(region, params)
                .with("Path", queue)
        );

        httpClient
                .sendAsync(req.build(), HttpResponse.BodyHandlers.ofString())
                .whenComplete(XmlResponseConsumer.of(callback));
    }

    // queue should be formatted like "/333517786763/EmailUpdate/"
    static public void sendMessage(XElement connection, String region, String queue, String message, Long delay, OperationOutcome<XElement> callback) {
        RecordStruct params = RecordStruct.record()
                .with("Action", "SendMessage")
                .with("MessageBody", message);

        if (delay != null)
            params.with("DelaySeconds", delay);

        HttpClient httpClient = HttpClient.newHttpClient();
        HttpRequest.Builder req = AWSUtilCore.buildRequest(connection, AWSSQS.buildHostOptionSQS(region, params)
                .with("Path", queue)
        );

        httpClient
                .sendAsync(req.build(), HttpResponse.BodyHandlers.ofString())
                .whenComplete(XmlResponseConsumer.of(callback));
    }

    static public RecordStruct buildHostOptionSQS(String region, RecordStruct params) {
        RecordStruct options = AWSSQS.buildHostOptionSQS(region);

        if (params.isFieldEmpty("Version"))
            params.with("Version", "2012-11-05");

        options.with("Params", params);

        return options;
    }

    static public RecordStruct buildHostOptionSQS(String region) {
        String host = "sqs.amazonaws.com";

        if (StringUtil.isNotEmpty(region)) {
            host = "sqs." + region + ".amazonaws.com";

            /* not supported yet - or different structure
            if (AWSUtilCore.isHostDualStack(region))
                host = "api.sqs." + region + ".aws";

             */
        }

        return RecordStruct.record()
                .with("Method", "GET")      // default, can override
                .with("Region", region)
                .with("Service", "sqs")
                .with("Host", host);
    }

    static public ListStruct extractIncomingMessages(XElement sqsmessage) {
        ListStruct result = ListStruct.list();

        if (sqsmessage == null)
            return result;

        List<XElement> messages = sqsmessage.selectAll("ReceiveMessageResult/Message");

        for (XElement message : messages) {
            RecordStruct entry = RecordStruct.record()
                    .with("ReceiptHandle", message.selectFirstText("ReceiptHandle").trim())
                    .with("QueueMessageId", message.selectFirstText("MessageId").trim());

            // anything coming in is fair game, but may not have a body, check in callers
            result.with(entry);

            String bjson = message.selectFirstText("Body");

            if (StringUtil.isNotEmpty(bjson)) {
                entry.with("Payload", bjson);
            }
        }

        return result;
    }

    static public ListStruct extractOutgoingEmailNotices(XElement sqsmessage) {
        ListStruct result = ListStruct.list();

        ListStruct list = AWSSQS.extractIncomingMessages(sqsmessage);

        for (int i = 00; i < list.size(); i++) {
            RecordStruct rec = list.getItemAsRecord(i);

            if (rec.isFieldEmpty("Payload"))
                continue;

            RecordStruct payload = rec.getFieldAsRecord("Payload");

            if (!"Notification".equals(payload.getFieldAsString("Type")))
                continue;

            //System.out.println("for body: " + brec.toPrettyString());
            //System.out.println();
            //System.out.println();

            RecordStruct mrec = payload.getFieldAsRecord("Message");

            if ((mrec != null) && mrec.isNotFieldEmpty("notificationType")) {
                String ntype = mrec.getFieldAsString("notificationType");

                //System.out.println("for message: " + mrec.toPrettyString());
                //System.out.println();
                //System.out.println();

                if (ntype.equals("Bounce") || ntype.equals("Delivery") || ntype.equals("Complaint")) {
                    rec
                            .with("Timestamp", Instant.parse(mrec.selectAsString(ntype.toLowerCase() + ".timestamp")))
                            .with("Payload", mrec);

                    result.with(rec);
                }
            }
        }

        return result;
    }

    static public ListStruct extractIncomingEmailNotices(XElement sqsmessage) {
        ListStruct result = ListStruct.list();

        ListStruct list = AWSSQS.extractIncomingMessages(sqsmessage);

        for (int i = 00; i < list.size(); i++) {
            RecordStruct rec = list.getItemAsRecord(i);

            if (rec.isFieldEmpty("Payload"))
                continue;

            RecordStruct payload = rec.getFieldAsRecord("Payload");

            if (!"Notification".equals(payload.getFieldAsString("Type")))
                continue;

            //System.out.println("for body: " + brec.toPrettyString());
            //System.out.println();
            //System.out.println();

            RecordStruct mrec = payload.getFieldAsRecord("Message");

            if ((mrec != null) && mrec.isNotFieldEmpty("notificationType")) {
                String ntype = mrec.getFieldAsString("notificationType");

                //System.out.println("for message: " + mrec.toPrettyString());
                //System.out.println();
                //System.out.println();

                if (ntype.equals("Received")) {
                    rec
                            .with("Timestamp", Instant.parse(mrec.selectAsString("mail.timestamp")))
                            .with("Payload", mrec);

                    result.with(rec);
                }
            }
        }

        return result;
    }
}
