package dcraft.tool.sentinel;

import dcraft.hub.op.OperatingContextException;
import dcraft.log.Logger;
import dcraft.service.work.InBoxQueuePollWork;
import dcraft.sql.SqlConnection;
import dcraft.sql.SqlUtil;
import dcraft.struct.ListStruct;
import dcraft.struct.RecordStruct;
import dcraft.task.ChainWork;
import dcraft.task.TaskContext;

public class MultiInBoxPollWork extends ChainWork {
    @Override
    protected void init(TaskContext taskctx) throws OperatingContextException {

        Logger.info("Start Checking SQS Queues for Sentinel InBox notifications");

        try (SqlConnection conn = SqlUtil.getConnection("sentinel-readwrite")) {
            ListStruct sqsqueues = conn.getResults("SELECT Alias FROM dca_aws_account WHERE SentinelInBoxQueueId IS NOT NULL");

            for (int i = 0; i < sqsqueues.size(); i++) {
                RecordStruct account = sqsqueues.getItemAsRecord(i);

                this.then(InBoxQueuePollWork.of(account.getFieldAsString("Alias")));
            }
        }
        catch (Exception x) {
            Logger.error("Error collecting inbox message queues: " + x);
        }
    }
}
