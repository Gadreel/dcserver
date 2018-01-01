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
import dcraft.hub.resource.ConfigResource;
import dcraft.service.BaseService;
import dcraft.service.IService;
import dcraft.service.ServiceRequest;

import java.util.HashMap;
import java.util.Map;

import dcraft.hub.app.ApplicationHub;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationOutcomeStruct;
import dcraft.xml.XElement;

/*
 * AuthService can not be remote, though it may call a remote service
 * 
 * VERIFY
 * 
 * auth token is key here - if present in request and we find it, then pass (no matter session id)
 * 	if present in request and we don't find it then reset to Guest and return error
 *  if not present then check creds
 *  
 * NOTICE
 * 
 * Simple service does not (yet - TODO) timeout on the auth tokens, will collect forever
 * 
 */

public class Tenants {
	static public boolean handle(ServiceRequest request, OperationOutcomeStruct callback, CoreDatabase db) throws OperatingContextException {
		if ("LoadMe".equals(request.getOp())) {
			return true;
		}
		
		if ("UpdateMe".equals(request.getOp())) {
			return true;
		}
		
		if ("LoadAll".equals(request.getOp())) {
			/*
			// AuthService should provide Tenants config, so no default is needed here
			XElement mdomains = ApplicationHub.getConfig().selectFirst("Tenants");
			
			if (mdomains == null) {
				Logger.error("No Tenants found");
				callback.returnResult();
			}
			
			ListStruct res = new ListStruct();
			
			for (XElement mdomain : mdomains.selectAll("Tenant")) {
				// this are just some of the possible names - others are in local - gives a taste of the names used
				ListStruct names = new ListStruct();
				
				for (XElement del : mdomain.selectAll("Name"))
					names.withItem(del.getText());
				
				String id = mdomain.getAttribute("Id");
				
				if (StringUtil.isEmpty(id))
					id = Integer.toString(res.size());
				
				res.withItem(RecordStruct.record()
						.with("Id", id)
						.with("Alias", mdomain.getAttribute("Alias"))
						.with("Title", mdomain.getAttribute("Title"))
						.with("Names", names)
						.with("Settings", Struct.objectToStruct(mdomain.find("Settings")))
				);
			}
			
			callback.returnValue(res);
			*/
			
			return true;
		}
		
		if ("Load".equals(request.getOp())) {
			// TODO code op
			callback.returnEmpty();
			return true;
		}
		
		if ("Update".equals(request.getOp())) {
			// TODO code op
			callback.returnEmpty();
			return true;
		}
		
		if ("Add".equals(request.getOp())) {
			// TODO code op
			callback.returnEmpty();
			return true;
		}
		
		if ("Import".equals(request.getOp())) {
			// TODO code op
			callback.returnEmpty();
			return true;
		}
		
		if ("Retire".equals(request.getOp())) {
			// TODO code op
			callback.returnEmpty();
			return true;
		}
		
		if ("Revive".equals(request.getOp())) {
			// TODO code op
			callback.returnEmpty();
			return true;
		}
		
		return false;
	}
}
