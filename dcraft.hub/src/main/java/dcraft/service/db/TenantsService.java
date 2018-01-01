package dcraft.service.db;

import dcraft.service.BaseService;
import dcraft.service.IService;
import dcraft.service.ServiceRequest;
import dcraft.xml.XElement;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationOutcomeStruct;

public class TenantsService extends BaseService {
	@Override
	public boolean handle(ServiceRequest request, OperationOutcomeStruct callback) throws OperatingContextException {
		/* TODO
		IDatabaseManager db = DatabaseHub.defaultDb();
		
		if (db == null) {
			Logger.errorTr(443);
			callback.returnResult();
			return false;
		}
		
		if (Logger.isDebug())
			Logger.debug("Tenants called with: " + request.getFeature() + " op: " + request.getOp());
		
		if ("Manager".equals(request.getFeature())) {
			if ("LoadAll".equals(request.getOp())) {
				DataRequest req = new DataRequest("dcLoadTenants")
					.withRootTenant();	// use root for this request
				
				db.submit(req, new StructOutomeFinal(callback));
				return true;
			}			
			
			if ("Load".equals(request.getOp())) {
				DataRequest req = new DataRequest("dcLoadTenant")
					.withParams(request.getDataAsRecord())
					.withRootTenant();	// use root for this request
				
				db.submit(req, new StructOutomeFinal(callback));
				return true;
			}			
		}
		*/
		
		return false;
	}
}
