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
package dcraft.db;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import dcraft.db.tables.ITablesContext;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
import dcraft.hub.op.OperationOutcomeStruct;
import dcraft.schema.SchemaResource;
import dcraft.service.ServiceRequest;
import dcraft.stream.StreamFragment;
import dcraft.struct.RecordStruct;
import dcraft.struct.Struct;

/**
 *
 */
public class DbServiceRequest extends ServiceRequest implements ITablesContext {
	// be in the calling context when you use this method, otherwise may miss the proper OpInfo def
	static public DbServiceRequest of(String proc) {
		DbServiceRequest req = new DbServiceRequest();
		req.name = "dcDatabase";
		req.feature = "ExecuteProc";
		req.op = proc;
		return req;
	}
	
	static public DbServiceRequest of(ServiceRequest src) {
		if (src instanceof DbServiceRequest)
			return (DbServiceRequest) src;
		
		DbServiceRequest req = new DbServiceRequest();
		req.name = src.getName();
		req.feature = src.getFeature();
		req.op = src.getOp();
		req.data = src.getData();
		req.def = src.getDefinition();
		req.outcome = src.getOutcome();
		req.fromRpc = src.isFromRpc();
		req.streamSource = src.getRequestStream();
		req.streamDest = src.getResponseStream();
		
		return req;
	}
	
	// set once in db service - internal
	protected boolean isReplicating = false;
	protected DatabaseAdapter ntrfc = null;
	protected BigDecimal stamp = null;
	protected List<String> tenants = null;
	protected RecordStruct replicateData = RecordStruct.record();			// a place to keep data for replication process
	
	public boolean isReplicating() {
		return this.isReplicating;
	}
	
	public void setReplicating(boolean v) {
		this.isReplicating = v;
	}
	
	public DbServiceRequest withReplicating(boolean v) {
		this.isReplicating = v;
		return this;
	}
	
	public RecordStruct getReplicateData() {
		return this.replicateData;
	}
	
	public DatabaseAdapter getInterface() {
		return this.ntrfc;
	}
	
	public void setInterface(DatabaseAdapter v) {
		this.ntrfc = v;
	}
	
	public DbServiceRequest withInterface(DatabaseAdapter v) {
		this.ntrfc = v;
		return this;
	}
	
	public BigDecimal getStamp() {
		return this.stamp;
	}
	
	public void setStamp(BigDecimal v) {
		this.stamp = v;
	}
	
	public DbServiceRequest withStamp(BigDecimal v) {
		this.stamp = v;
		return this;
	}
	
	public String getTenant() throws OperatingContextException {
		if ((this.tenants == null) || (this.tenants.size() == 0)) {
			Struct params = this.getData();
			
			if ((params instanceof RecordStruct) && ((RecordStruct)params).isNotFieldEmpty("ForTenant"))
				return ((RecordStruct)params).getFieldAsString("ForTenant");
			
			return OperationContext.getOrThrow().getUserContext().getTenantAlias();
		}
		
		return this.tenants.get(this.tenants.size() - 1);
	}
	
	public void pushTenant(String did) {
		if (this.tenants == null)
			this.tenants = new ArrayList<>();
		
		this.tenants.add(did);
	}
	
	public void popTenant() {
		if (this.tenants != null)
			this.tenants.remove(this.tenants.size() - 1);
	}
}
