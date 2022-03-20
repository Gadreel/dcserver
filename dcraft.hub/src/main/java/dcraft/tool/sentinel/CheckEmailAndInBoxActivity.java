package dcraft.tool.sentinel;

import dcraft.hub.op.OperatingContextException;
import dcraft.log.Logger;
import dcraft.task.ChainWork;
import dcraft.task.TaskContext;

public class CheckEmailAndInBoxActivity extends ChainWork {
	@Override
	protected void init(TaskContext taskctx) throws OperatingContextException {
		Logger.info("Start Sentinel Email and InBox Activity BATCH");

		this
				.then(new MultiInBoxPollWork())
				.then(EmailActivityPollWork.of())
				.then(EmailActivityProcessWork.of())
				.then(taskctx1 -> {
					Logger.info("End Sentinel Email and InBox Activity BATCH");
					taskctx.complete();
				})
		;
	}
}
