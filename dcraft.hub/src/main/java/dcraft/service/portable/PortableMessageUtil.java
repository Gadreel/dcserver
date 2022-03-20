package dcraft.service.portable;

import dcraft.hub.ResourceHub;
import dcraft.hub.app.ApplicationHub;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
import dcraft.hub.op.OperationOutcome;
import dcraft.hub.op.OperationOutcomeEmpty;
import dcraft.hub.resource.KeyRingResource;
import dcraft.interchange.aws.AWSSQS;
import dcraft.log.Logger;
import dcraft.service.work.EncryptPortableMessageWork;
import dcraft.sql.SqlConnection;
import dcraft.sql.SqlUtil;
import dcraft.struct.BaseStruct;
import dcraft.struct.RecordStruct;
import dcraft.struct.Struct;
import dcraft.struct.scalar.StringStruct;
import dcraft.task.ChainWork;
import dcraft.task.TaskHub;
import dcraft.util.Memory;
import dcraft.util.RndUtil;
import dcraft.util.StringUtil;
import dcraft.util.TimeUtil;
import dcraft.util.pgp.ClearsignUtil;
import dcraft.xml.XElement;

import java.io.IOException;
import java.io.InputStream;

public class PortableMessageUtil {
    static public void sendMessageToRemoteTenantByQueue(Long tenantid, String op, BaseStruct data, Long delaysec, RecordStruct replypayload, OperationOutcomeEmpty callback) throws OperatingContextException {
        RecordStruct destinfo = null;
        RecordStruct replymethod = null;

        try (SqlConnection conn = SqlUtil.getConnection("sentinel-readwrite")) {
            destinfo = conn.getRow("SELECT sqs.Region QueueRegion, sqs.QueuePath, sqs.Id, acc.Alias, acc.Id AccountId, dep.Alias Deployment, dep.OnlineStatus, ten.Alias Tenant\n" +
                    "FROM dca_aws_sqs sqs\n" +
                    "    INNER JOIN dca_aws_account acc ON (sqs.AccountId = acc.Id)\n" +
                    "    INNER JOIN dca_deployment dep ON (sqs.Id = dep.InBoxQueueId)\n" +
                    "    INNER JOIN dca_deployment_tenant ten ON (dep.Id = ten.DeploymentId)\n" +
                    "WHERE sqs.QueueType = 2 AND ten.Id = ?", tenantid);

            if (replypayload != null) {
                replymethod = conn.getRow("SELECT sqs.Region QueueRegion, sqs.QueuePath\n" +
                        "FROM dca_aws_sqs sqs\n" +
                        "    INNER JOIN dca_aws_account acc ON (sqs.Id = acc.SentinelInBoxQueueId)\n" +
                        "WHERE sqs.QueueType = 2 AND acc.Id = ?", destinfo.getFieldAsInteger("AccountId"));
            }
        }
        catch (Exception x) {
            Logger.error("Error sending message to remote tenant, tenant info did not load: " + tenantid + " - " + x);
            if (callback != null)
                callback.returnEmpty();
            return;
        }

        if (destinfo == null) {
            Logger.error("Error sending message to remote tenant, tenant info not found: " + tenantid + " - " + op);
            if (callback != null)
                callback.returnEmpty();
            return;
        }

        if ((replypayload != null) && (replymethod == null)) {
            Logger.error("Error sending message to remote tenant, reply method not found: " + tenantid + " - " + op);
            if (callback != null)
                callback.returnEmpty();
            return;
        }

        RecordStruct sendinfo = RecordStruct.record()
                .with("Type", "Queue")
                .with("EncryptTo", "encryptor@" + destinfo.getFieldAsString("Deployment") + ".dc")      // TODO support key override in database
                .with("QueueRegion", destinfo.getFieldAsString("QueueRegion"))
                .with("QueuePath", destinfo.getFieldAsString("QueuePath"))
                .with("DelaySeconds", delaysec);

        RecordStruct message = PortableMessageUtil.buildCoreMessage(null, null);

        message
                .with("Destination", RecordStruct.record()
                    .with("Deployment", destinfo.getFieldAsString("Deployment"))
                    .with("Tenant", destinfo.getFieldAsString("Tenant"))
                )
                .with("Payload", RecordStruct.record()
                        .with("Op", op)
                        .withConditional("Body", data)
                );

        if (replypayload != null) {
            replymethod.with("Type", "Queue");
            replymethod.with("EncryptTo", "encryptor@" + ApplicationHub.getDeployment() + ".dc");           // TODO support overrides someday

            // skip Destination, let it default to our Source
            message.with("Reply", RecordStruct.record()
                    .with("Payload", replypayload)
                    .with("Method", replymethod)
            );
        }

        String alias = destinfo.getFieldAsString("Alias");			// aws account alias

        PortableMessageUtil.sendMessageByQueue(message, sendinfo, alias, callback);
    }

    static public void sendMessageByQueue(RecordStruct message, RecordStruct sendinfo, String alt, OperationOutcomeEmpty callback) throws OperatingContextException {
        if ((message == null) || ! message.validate("PortableMessage")) {
            Logger.error("Error sending message to queue, message missing or invalid");
            if (callback != null)
                callback.returnEmpty();
            return;
        }

        String mid = message.getFieldAsString("MessageId");

        XElement settings = ApplicationHub.getCatalogSettings("Interchange-Aws", alt);

        if (settings == null) {
            Logger.error("Error sending message to queue, settings not found: " + mid);
            if (callback != null)
                callback.returnEmpty();
            return;
        }

        String region = sendinfo.getFieldAsString("QueueRegion");			// queue region
        String path = sendinfo.getFieldAsString("QueuePath");			// queue path

        String deployment = message.selectAsString("Destination.Deployment");

        Logger.info("Queue message to: " + deployment + " - " + mid);

        if (callback == null)
            callback = new OperationOutcomeEmpty() {
                @Override
                public void callback() throws OperatingContextException {
                    if (this.hasErrors()) {
                        Logger.warn("Problem sending to inbox queue: " + deployment + " - " + mid);
                    }
                    else {
                        Logger.info("Sent to inbox queue: " + deployment + " - " + mid);
                    }
                }
            };

        OperationOutcomeEmpty finalCallback = callback;

        ChainWork work = ChainWork
                .of(EncryptPortableMessageWork.of(message, sendinfo))
                .then(taskctx -> {
                    String payload = Struct.objectToString(taskctx.getParams());

                    if (StringUtil.isNotEmpty(payload)) {
                        AWSSQS.sendMessage(settings, region, path, payload, sendinfo.getFieldAsInteger("DelaySeconds"), new OperationOutcome<>() {
                            @Override
                            public void callback(XElement result) {
                                finalCallback.returnEmpty();
                            }
                        });
                    }

                    taskctx.returnEmpty();
                });

        TaskHub.submit(work);
    }

    static public RecordStruct extractReplyMethod(RecordStruct originalmsg) {
        if ((originalmsg == null) || ! originalmsg.validate("PortableMessage"))
            return null;

        RecordStruct reply = originalmsg.getFieldAsRecord("Reply");

        if (reply == null)
            return null;

        return reply.getFieldAsRecord("Method");
    }

    static public RecordStruct buildReplyMessage(BaseStruct result, Long exitcode, String exitmsg, RecordStruct originalmsg) throws OperatingContextException {
        if ((originalmsg == null) || ! originalmsg.validate("PortableMessage"))
            return null;

        RecordStruct reply = originalmsg.getFieldAsRecord("Reply");

        if (reply == null)
            return null;

        RecordStruct newmessage = PortableMessageUtil.buildCoreMessage(exitcode, exitmsg);

        newmessage
                .with("ReplyToMessageId", originalmsg.getFieldAsString("MessageId"));

        if (reply.isNotFieldEmpty("Destination")) {
            newmessage.with("Destination", reply.getFieldAsRecord("Destination"));
        }
        else {
            RecordStruct dest = RecordStruct.record();

            dest.copyFields(originalmsg.getFieldAsRecord("Source"), "Node");

            newmessage.with("Destination", dest);
        }

        RecordStruct newpayload = RecordStruct.record();

        RecordStruct payinfo = reply.getFieldAsRecord("Payload");

        newpayload.with("Op", payinfo.getFieldAsString("Op"));

        if (payinfo.getFieldAsBooleanOrFalse("IncludeResult")) {
            BaseStruct proxyresult = payinfo.getField("ResultProxy");

            if (proxyresult == null) {
                if (result != null) {
                    newpayload.with("Body", result);
                }
            }
            else {
                if ((result != null) && (proxyresult instanceof RecordStruct)) {
                    ((RecordStruct) proxyresult).with("Result", result);
                }

                newpayload.with("Body", proxyresult);
            }
        }

        newmessage
                .with("Payload", newpayload);

        return newmessage;
    }

    static public RecordStruct buildCoreMessage(Long exitcode, String exitmsg) throws OperatingContextException {
        RecordStruct newmessage = RecordStruct.record()
                .with("MessageId", RndUtil.nextUUId())
                .with("Version",  "2022.1")       // TODO configure
                .with("Timestamp", TimeUtil.now())
                .with("Expires", TimeUtil.now().plusDays(14))       // TODO configure, maybe allow override
                .with("Source", RecordStruct.record()
                        .with("Deployment", ApplicationHub.getDeployment())
                        .with("Node", ApplicationHub.getNodeId())
                        .with("Tenant", OperationContext.getOrThrow().getUserContext().getTenantAlias())
                        .with("Site", OperationContext.getOrThrow().getUserContext().getSiteAlias())
                );

        if (exitcode != null)
            newmessage.with("Code", exitcode).with("Message", exitmsg);

        return newmessage;
    }

    static public CharSequence verifyPortableMessage(CharSequence message) {
        if (StringUtil.isEmpty(message)) {
            Logger.error("Unable to verify the portable message signature, missing the message");
            return null;
        }

        KeyRingResource keys = ResourceHub.getResources().getKeyRing();

        StringBuilder sb = new StringBuilder();
        StringStruct sig = StringStruct.ofEmpty();
        StringStruct key = StringStruct.ofEmpty();

        Memory binmsg = new Memory(message);

        binmsg.setPosition(0);

        try (InputStream bais = binmsg.getInputStream()) {
            ClearsignUtil.verifyFile(bais, keys, sb, sig, key);

            if (sig.isEmpty()) {
                Logger.error("Unable to verify the portable message signature");

                if (key.isEmpty())
                    Logger.error("No key found");
                else
                    Logger.error("Tried key: " + key);
            }
            else {
                Logger.info("Verified the portable message signature with key: " + key);

                return sb;
            }
        }
        catch (IOException x) {
            Logger.error("Unable to read the raw Portable Message: " + x);
        }

        return null;
    }

    static public RecordStruct sendToForSentinel(String alt) {
        XElement settings = ApplicationHub.getCatalogSettings("Interchange-Aws", alt);

        if (settings == null) {
            Logger.error("Error sending portable message to sentinel, aws settings not found");
            return null;
        }

        return RecordStruct.record()
                .with("Type", "Queue")
                .with("EncryptTo", "encryptor@sentinel.dc")      // TODO support key override
                .with("QueueRegion", settings.getAttribute("SQSRegion"))
                .with("QueuePath", settings.getAttribute("SentinelQueue"));
    }
}
