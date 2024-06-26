/* ************************************************************************
#
#  designCraft.io
#
#  http://designcraft.io/
#
#  Copyright:
#    Copyright 2012 eTimeline, LLC. All rights reserved.
#
#  License:
#    See the license.txt file in the project's top-level directory for details.
#
#  Authors:
#    * Andy White
#
************************************************************************ */
package dcraft.db.rocks;

import dcraft.db.DatabaseAdapter;
import dcraft.db.DatabaseException;
import dcraft.db.IConnectionManager;
import dcraft.db.util.DbUtil;
import dcraft.hub.op.OperationOutcomeStruct;
import dcraft.hub.op.UserContext;
import dcraft.log.count.CountHub;
import dcraft.service.BaseService;
import dcraft.service.IService;
import dcraft.service.ServiceRequest;

import dcraft.db.DbServiceRequest;
import dcraft.db.proc.IStoredProc;
import dcraft.hub.ResourceHub;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
import dcraft.log.Logger;
import dcraft.schema.DbProc;
import dcraft.struct.BaseStruct;
import dcraft.struct.RecordStruct;
import dcraft.struct.Struct;
import dcraft.xml.XElement;

/**
 * 
 * @author Andy
 *
 */
public class Service extends BaseService {
	// do not extend BaseDataService - could bypass security

	@Override
	public void start() {
		Logger.info(0, "dcDatabase Service Started");
		
		super.start();
    }
		
	public boolean handle(ServiceRequest request, OperationOutcomeStruct callback) throws OperatingContextException {
		// message was sent, record it here
		//CountHub.countObjects("dcBusMessageSent", request.getName());
		
		if (Logger.isDebug())
			Logger.debug("DB call being handled for : " + request.getName());
		
		try {
			BaseStruct params = request.getData();
			
			// only a sysadmin can ask for tenant changes remotely
			if ((params instanceof RecordStruct) && ((RecordStruct)params).isNotFieldEmpty("_ForTenant")) {
				String talias = ((RecordStruct)params).getFieldAsString("_ForTenant");
				UserContext usr = OperationContext.getOrThrow().getUserContext();
				
				if (request.isFromRpc() && ! usr.getTenantAlias().equals(talias) && ! usr.isTagged("SysAdmin")) {
					Logger.error("May not call database with this tenant, your tenant alias is: " + talias);
					callback.returnEmpty();
				}
			}
			
			return this.submit(DbServiceRequest.of(request));
		}
		catch(Exception x) {
			Logger.error("Unhandled exception in DB call: " + request.getName() + " - " + x);
			callback.returnEmpty();
		}
		
		return false;
	}
	
	public boolean submit(DbServiceRequest request) throws OperatingContextException {
		/* ???
		if (this.isOffline()) {
			Logger.errorTr(308);		
			request.getOutcome().returnResult();
			return false;
		}
		*/

		BaseStruct params = request.getData();
		
		String reqdbname = (params instanceof RecordStruct)
				? ((RecordStruct)params).getFieldAsString("_Database", "default")
				: "default";
		
		IConnectionManager conn = ResourceHub.getResources().getDatabases().getDatabase(reqdbname);
		
		if (conn == null) {
			Logger.error("Missing database: " + reqdbname);
			request.getOutcome().returnResult();
			return false;
		}

		try {
			return DbUtil.execute(request, conn);
		}
		catch (DatabaseException x) {
			Logger.error("Error with database routine: " + x);
			request.getOutcome().returnResult();
			return false;
		}
	}
}
