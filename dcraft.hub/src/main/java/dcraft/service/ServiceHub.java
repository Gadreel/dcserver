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
package dcraft.service;

import dcraft.hub.ResourceHub;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationOutcomeStruct;
import dcraft.log.Logger;
import dcraft.log.count.CountHub;

public class ServiceHub {
	static public boolean call(IServiceRequestBuilder request, OperationOutcomeStruct callback) throws OperatingContextException {
		return ServiceHub.call(request.toServiceRequest().withOutcome(callback));
	}

	static public boolean call(IServiceRequestBuilder request) throws OperatingContextException {
		return ServiceHub.call(request.toServiceRequest());
	}
	
	static public boolean call(ServiceRequest request) throws OperatingContextException {
		OperationOutcomeStruct callback = request.requireOutcome();
		
		// if validate failed, there will be errors
		if (! request.validate()) {
			callback.returnEmpty();
			return false;
		}
		
		// message was sent, record it here
		CountHub.countObjects("dcBusMessageSent", request.getName());
		
        if (Logger.isDebug())
        	Logger.debug("Message being handled for : " + request.getName());
		
        try {
			return ResourceHub.getResources().getServices().handle(request);
        }
		catch(Exception x) {	
			Logger.error("Unhandled exception in service: " + request.getName() + " - " + x);
			callback.returnEmpty();
			return false;
		}
    }
}
