package dcraft.db.work;

import dcraft.db.request.DataRequest;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationOutcomeStruct;
import dcraft.service.ServiceHub;
import dcraft.struct.Struct;
import dcraft.task.IWork;
import dcraft.task.TaskContext;

public class BatchProcCall implements IWork {
	static public BatchProcCall of(String name) {
		BatchProcCall work = new BatchProcCall();
		work.proc = name;
		return work;
	}

	protected String proc = null;

	@Override
	public void run(TaskContext taskctx) throws OperatingContextException {
		ServiceHub.call(DataRequest.of(this.proc).withForTenant(TaskContext.getOrThrow().getTenant().getAlias()), new OperationOutcomeStruct() {
			@Override
			public void callback(Struct result) throws OperatingContextException {
				taskctx.returnEmpty();
			}
		});
	}
}
