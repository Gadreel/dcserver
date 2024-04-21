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

import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationOutcomeStruct;
import dcraft.hub.resource.ResourceTier;
import dcraft.schema.SchemaResource;
import dcraft.schema.ServiceSchema;
import dcraft.xml.XElement;
 
public interface IService {
	void init(String name, XElement config, ResourceTier tier);
	String getName();
	boolean isEnabled();
	
	void start();
	void stop();

	SchemaResource.OpInfo lookupOpInfo(ServiceRequest request) throws OperatingContextException;

	// true if handled
	boolean handle(ServiceRequest request, OperationOutcomeStruct callback) throws OperatingContextException;
}
