package dcraft.session;

import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
import dcraft.hub.op.OperationOutcomeStruct;
import dcraft.log.DebugLevel;
import dcraft.log.Logger;
import dcraft.service.BaseService;
import dcraft.service.IService;
import dcraft.xml.XElement;

/**
 */
public class Service extends BaseService {
	public boolean handle(dcraft.service.ServiceRequest request, OperationOutcomeStruct callback) throws OperatingContextException {
		if ("Manager".equals(request.getFeature())) {
			if ("Touch".equals(request.getOp())) {
				Session s = SessionHub.lookup(request.getData().toString());
				
				if (s != null)
					s.touch();
				else
					Logger.error("Unable to touch session, missing Id or Code or session terminated.");
				
				callback.returnEmpty();
				return true;
			}
			
			if ("DebugLevel".equals(request.getOp())) {
				Session s = SessionHub.lookup(request.getDataAsRecord().getFieldAsString("Session"));
				
				if (s != null)
					s.withLevel(DebugLevel.parse(request.getDataAsRecord().getFieldAsString("Level")));
				else
					Logger.error("Unable to touch session, missing Id or Code or session terminated.");
				
				callback.returnEmpty();
				return true;
			}
			
			if ("LoadUser".equals(request.getOp())) {
				Session s = SessionHub.lookup(request.getData().toString());
				
				if (s != null) {
					callback.returnValue(s.getUser().deepCopyExclude("AuthToken"));
				}
				else {
					Logger.error("Unable to touch session, missing Id or Code or session terminated.");
					callback.returnEmpty();
				}
				
				return true;
			}
		}
		else if ("Session".equals(request.getFeature())) {
			Session mine = OperationContext.getOrThrow().getSession();
			
			if (mine != null)
				return mine.handle(request, callback);
			
			Logger.error( "Unable to use session, missing Id or Code or session already terminated.");
			callback.returnEmpty();
			return true;
		}
		
		return false;
	}
}
