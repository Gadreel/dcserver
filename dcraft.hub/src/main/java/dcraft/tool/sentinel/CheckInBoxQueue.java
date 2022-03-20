package dcraft.tool.sentinel;

import dcraft.hub.op.OperatingContextException;
import dcraft.log.Logger;
import dcraft.service.work.InBoxQueuePollWork;
import dcraft.task.ChainWork;
import dcraft.task.TaskContext;

public class CheckInBoxQueue extends ChainWork {
	@Override
	protected void init(TaskContext taskctx) throws OperatingContextException {
		Logger.info("Start Sentinel InBox Queue BATCH");

		this
				.then(new MultiInBoxPollWork())
				.then(taskctx1 -> {
					Logger.info("End Sentinel InBox Queue BATCH");
					taskctx.complete();
				})
		;
	}
}
