package dcraft.service.work;

import dcraft.hub.op.*;
import dcraft.service.ServiceHub;
import dcraft.service.ServiceRequest;
import dcraft.service.portable.PortableMessageUtil;
import dcraft.struct.BaseStruct;
import dcraft.struct.RecordStruct;
import dcraft.task.IWork;
import dcraft.task.TaskContext;
import dcraft.xml.XElement;

public class PortableRequestProcessWork implements IWork {
    static public PortableRequestProcessWork of(RecordStruct msg) {
        PortableRequestProcessWork work = new PortableRequestProcessWork();
        work.message = msg;
        return work;
    }

    protected RecordStruct message = null;

    @Override
    public void run(TaskContext taskctx) throws OperatingContextException {
        if ((this.message == null) || ! this.message.validate("PortableMessage")) {
            taskctx.returnEmpty();
            return;
        }

        // start clean so we can detect errors only related to the service call
        taskctx.clearExitCode();

        RecordStruct payload = this.message.getFieldAsRecord("Payload");
        RecordStruct destination = this.message.selectAsRecord("Destination");

        String calltenant = destination.getFieldAsString("Tenant");

        String basetenant = taskctx.getUserContext().getTenantAlias();
        String basesite = taskctx.getUserContext().getSiteAlias();

        // switch context tenant temporarily - this is a cheat and normally not cool, but it should be fine in this case
        OperationContext.getOrThrow().getUserContext()
                .withTenantAlias(calltenant)
                .withSiteAlias("root");

        ServiceHub.call(
                ServiceRequest
                    .of(payload.getFieldAsString("Op"))
                    .withData(payload.getField("Body"))
                    .withOutcome(new OperationOutcomeStruct() {
                        @Override
                        public void callback(BaseStruct result) throws OperatingContextException {
                            RecordStruct replyMessage = PortableMessageUtil.buildReplyMessage(result, taskctx.getExitCode(),
                                    taskctx.getExitMessage(), PortableRequestProcessWork.this.message);

                            // only if a reply is requested should we try to reply
                            if (replyMessage != null) {
                                RecordStruct replyMethod = PortableMessageUtil.extractReplyMethod(PortableRequestProcessWork.this.message);

                                // TODO support other reply method types

                                PortableMessageUtil.sendMessageByQueue(replyMessage, replyMethod, null, new OperationOutcomeEmpty() {
                                    @Override
                                    public void callback() throws OperatingContextException {
                                        // return to the root (original) tenant/site
                                        OperationContext.getOrThrow().getUserContext()
                                                .withTenantAlias(basetenant)
                                                .withSiteAlias(basesite);

                                        taskctx.returnEmpty();
                                    }
                                });
                            }
                            else {
                                taskctx.returnEmpty();
                            }
                        }
                    })
        );
    }
}
