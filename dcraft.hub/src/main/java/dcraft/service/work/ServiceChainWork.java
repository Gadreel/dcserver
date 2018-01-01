package dcraft.service.work;

import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationOutcomeStruct;
import dcraft.service.ServiceHub;
import dcraft.service.ServiceRequest;
import dcraft.struct.Struct;
import dcraft.task.IWork;
import dcraft.task.TaskContext;

/*
 * do not to use ServiceChainWork where an Outcome or Data is set, in and out are via the chain
 */
public class ServiceChainWork implements IWork {
	static public ServiceChainWork of(ServiceRequest request) {
		ServiceChainWork w = new ServiceChainWork();
		w.request = request;
		return w;
	}
	
	protected ServiceRequest request = null;
	
	@Override
	public void run(TaskContext taskctx) throws OperatingContextException {
		this.request
			.withData(taskctx.getParams())
			.withOutcome(new OperationOutcomeStruct() {
			@Override
			public void callback(Struct result) throws OperatingContextException {
				taskctx.returnValue(result);
			}
		});
		
		ServiceHub.call(this.request);
	}
}
