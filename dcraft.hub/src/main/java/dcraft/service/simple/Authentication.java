/* ************************************************************************
#
#  designCraft.io
#
#  http://designcraft.io/
#
#  Copyright:
#    Copyright 2014 eTimeline, LLC. All rights reserved.
#
#  License:
#    See the license.txt file in the project's top-level directory for details.
#
#  Authors:
#    * Andy White
#
************************************************************************ */
package dcraft.service.simple;

import dcraft.hub.ResourceHub;
import dcraft.hub.op.OperationContext;
import dcraft.hub.op.UserContext;
import dcraft.log.Logger;
import dcraft.service.ServiceRequest;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationOutcomeStruct;
import dcraft.session.Session;
import dcraft.struct.RecordStruct;
import dcraft.task.StateWorkStep;
import dcraft.util.StringUtil;

public class Authentication {
	static public boolean handle(ServiceRequest request, OperationOutcomeStruct callback, CoreDatabase db) throws OperatingContextException {
		String op = request.getOp();

		OperationContext ctx = OperationContext.getOrThrow();
		UserContext uc = ctx.getUserContext();
		
		TenantData du = db.getTenant(uc.getTenantAlias());
		
		if (du == null) {
			uc.clearToGuest();
			
			Session sess = ctx.getSession();
			
			if (sess != null)
				sess.userChanged();
			
			Logger.errorTr(442);
			Logger.error("Tenant not found");
			callback.returnEmpty();
			
			return true;
		}
		
		if ("Verify".equals(op)) {
			du.verifyToken(uc.getAuthToken(), callback);
			
			return true;
		}
		
		if ("SignIn".equals(op)) {
			RecordStruct rec = request.getDataAsRecord();
			
			String uname = rec.getFieldAsString("Username").toLowerCase();
			String passwd = rec.getFieldAsString("Password");
			
			du.verifyCreds(uname, passwd, callback);
			
			return true;
		}
		
		if ("SignOut".equals(op)) {
			du.clear(uc.getAuthToken(), callback);

			return true;
		}

		return false;
	}
}
