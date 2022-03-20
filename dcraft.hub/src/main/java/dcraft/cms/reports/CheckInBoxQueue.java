package dcraft.cms.reports;

import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationOutcomeStruct;
import dcraft.log.Logger;
import dcraft.service.ServiceHub;
import dcraft.service.ServiceRequest;
import dcraft.service.work.InBoxQueuePollWork;
import dcraft.struct.BaseStruct;
import dcraft.task.ChainWork;
import dcraft.task.IWork;
import dcraft.task.TaskContext;

public class CheckInBoxQueue extends ChainWork {
	@Override
	protected void init(TaskContext taskctx) throws OperatingContextException {
		Logger.info("Start InBox Queue BATCH");

		this
				.then(InBoxQueuePollWork.of(null))
				.then(taskctx1 -> {
					Logger.info("End InBox Queue BATCH");
					taskctx.complete();
				})
		;
	}
}
