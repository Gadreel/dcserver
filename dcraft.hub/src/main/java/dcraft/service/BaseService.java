package dcraft.service;

import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
import dcraft.hub.op.OperationMarker;
import dcraft.hub.resource.ResourceTier;
import dcraft.log.Logger;
import dcraft.schema.DataType;
import dcraft.schema.SchemaHub;
import dcraft.schema.SchemaResource;
import dcraft.stream.record.VerifyRecordStream;
import dcraft.struct.BaseStruct;
import dcraft.xml.XElement;

/**
 */
abstract public class BaseService implements IService {
	protected String name = null;
	protected ResourceTier tier = null;
	protected boolean enabled = false;
	protected XElement config = null;
	
	@Override
	public String getName() {
		return this.name;
	}
	
	@Override
	public boolean isEnabled() {
		return this.enabled;
	}
	
	@Override
	public void init(String name, XElement config, ResourceTier tier) {
		this.name = name;
		this.config = config;
		this.tier = tier;	// creates a circular reference, but will be found/resolved in config reload
	}

	@Override
	public SchemaResource.OpInfo lookupOpInfo(ServiceRequest request) throws OperatingContextException {
		return SchemaHub.getServiceOpInfo(request.name, request.feature, request.op);
	}

	@Override
	public void start() {
		this.enabled = true;
	}
	
	@Override
	public void stop() {
		this.enabled = false;
	}
}
