package dcraft.mail.sender;

import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationOutcomeComposite;
import dcraft.interchange.aws.AWSSMS;
import dcraft.struct.CompositeStruct;
import dcraft.struct.RecordStruct;
import dcraft.task.IWork;
import dcraft.task.TaskContext;

public class AwsSimpleMailServiceHttpWork implements IWork {
    protected RecordStruct params = null;

    public AwsSimpleMailServiceHttpWork() {
    }

    public AwsSimpleMailServiceHttpWork(RecordStruct params) {
        this.params = params;
    }

    @Override
    public void run(TaskContext taskctx) throws OperatingContextException {
        // params in should be like what we see in MailUtil.createSimpleSMSParams
        RecordStruct paramsIn = (this.params != null) ? this.params : taskctx.getFieldAsRecord("Params");

        AWSSMS.sendEmail(null, paramsIn.getFieldAsRecord("AwsMailHttp"), new OperationOutcomeComposite() {
            @Override
            public void callback(CompositeStruct result) throws OperatingContextException {
                System.out.println("done: " + result);

                RecordStruct transresult = RecordStruct.record()
                        .with("Success", ! taskctx.hasExitErrors());

                if (this.isNotEmptyResult()) {
                    RecordStruct res = (RecordStruct) result;

                    // example be MessageId
                    // { "MessageId": "0100018442c32ad3-a4405d3a-f7ec-4133-b30f-7c00ca6794c6-000000" }
                    //
                    // in the email
                    // Message-ID: <0100018442c32ad3-a4405d3a-f7ec-4133-b30f-7c00ca6794c6-000000@email.amazonses.com>

                    String messageId = "<" + res.getFieldAsString("MessageId") + "@email.amazonses.com>";

                    transresult.with("MessageId", messageId);
                }

                taskctx.returnValue(paramsIn.with("Transport", transresult));
            }
        });
    }
}
