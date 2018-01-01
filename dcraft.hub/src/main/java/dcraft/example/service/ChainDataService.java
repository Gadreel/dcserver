package dcraft.example.service;

import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationOutcomeStruct;
import dcraft.service.BaseService;
import dcraft.service.ServiceRequest;
import dcraft.struct.RecordStruct;

/*
 * 
 */
public class ChainDataService extends BaseService {
	@Override
	public boolean handle(ServiceRequest request, OperationOutcomeStruct outcome) throws OperatingContextException {
		if ("Simple".equals(request.getOp())) {
			System.out.println("Service got " + request.getData());
			
			outcome.returnValue(RecordStruct.record()
					.with("Dish", "Mac and Cheese")
					.with("Judge", "Janet")
					.with("Rating", 7.0));
	
			// true means we are able to process the request (even if the data was not found)
			return true;
		}
		
		return false;
	}
}