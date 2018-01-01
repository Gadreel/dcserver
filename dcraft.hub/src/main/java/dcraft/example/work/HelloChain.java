package dcraft.example.work;

import dcraft.hub.op.OperatingContextException;
import dcraft.log.Logger;
import dcraft.struct.RecordStruct;
import dcraft.task.ChainWork;
import dcraft.task.IWork;
import dcraft.task.TaskContext;

public class HelloChain extends ChainWork {
	@Override
	protected void init(TaskContext taskctx) throws OperatingContextException {
		Logger.info("Start nightly BATCH");
		
		this.then(new HelloWork())
				.then(new IWork() {
					@Override
					public void run(TaskContext taskctx) throws OperatingContextException {
						taskctx.returnValue(RecordStruct.record().with("Greet", "Jerry"));
					}
				})
				.then(new SlowGreetWork());
	}
}
