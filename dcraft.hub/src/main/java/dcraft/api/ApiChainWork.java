package dcraft.api;

import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationOutcomeStruct;
import dcraft.log.Logger;
import dcraft.service.ServiceHub;
import dcraft.service.ServiceRequest;
import dcraft.struct.Struct;
import dcraft.task.IWork;
import dcraft.task.TaskContext;

/*
 * do not to use ApiChainWork where an Outcome or Data is set, in and out are via the chain
 */
public class ApiChainWork implements IWork {
	static public ApiChainWork of(ApiSession sess, ServiceRequest request) {
		ApiChainWork w = new ApiChainWork();
		w.request = request;
		w.sess = sess;
		return w;
	}
	
	protected ApiSession sess = null;
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
		
		if (this.sess != null) {
			this.sess.call(this.request);
			this.sess = null;
		}
		else {
			Logger.error("ApiChainWork missing api session");
			this.request.requireOutcome().returnEmpty();
		}
	}
}
