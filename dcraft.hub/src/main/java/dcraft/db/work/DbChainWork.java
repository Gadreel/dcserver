package dcraft.db.work;

import dcraft.db.request.DataRequest;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationOutcomeStruct;
import dcraft.service.ServiceHub;
import dcraft.service.ServiceRequest;
import dcraft.struct.BaseStruct;
import dcraft.struct.Struct;
import dcraft.task.IWork;
import dcraft.task.TaskContext;

/*
 * do not to use ServiceChainWork where an Outcome or Data is set, in and out are via the chain
 */
public class DbChainWork implements IWork {
	static public DbChainWork of(DataRequest request) {
		DbChainWork w = new DbChainWork();
		w.request = request;
		return w;
	}
	
	protected DataRequest request = null;
	
	@Override
	public void run(TaskContext taskctx) throws OperatingContextException {
		ServiceRequest srequest = this.request.toServiceRequest();
		
		srequest.withData(taskctx.getParams());
		
		srequest.withOutcome(new OperationOutcomeStruct() {
			@Override
			public void callback(BaseStruct result) throws OperatingContextException {
				taskctx.returnValue(result);
			}
		});
		
		ServiceHub.call(this.request);
	}
}
