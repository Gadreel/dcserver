package dcraft.db.proc.call;

import dcraft.db.proc.IUpdatingStoredProc;
import dcraft.db.ICallContext;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
import dcraft.hub.op.OperationOutcomeStruct;
import dcraft.hub.op.UserContext;
import dcraft.log.Logger;
import dcraft.session.Session;
import dcraft.util.StringUtil;

public class SignOut implements IUpdatingStoredProc {
	@Override
	public void execute(ICallContext request, OperationOutcomeStruct callback) throws OperatingContextException {
		signout(request);
		
		callback.returnEmpty();
	}
	
	static public void signout(ICallContext request) throws OperatingContextException {
		OperationContext ctx = OperationContext.getOrThrow();
		UserContext uc = ctx.getUserContext();
		
		try {
			String token = uc.getAuthToken();
			
			if (StringUtil.isEmpty(token))
				Logger.errorTr(117);
			else 
				request.getInterface().kill("root", "dcSession", token);
		}
		catch (Exception x) {
			Logger.error("SignOut: Unable to remove session global: " + x);
		}
		
		uc.clearToGuestKeepSite();
		
		Session sess = ctx.getSession();
		
		if (sess != null)
			sess.userChanged();
	}
}
