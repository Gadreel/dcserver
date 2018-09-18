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
package dcraft.db.request;

import dcraft.db.DbServiceRequest;
import dcraft.db.work.DbChainWork;
import dcraft.service.IServiceRequestBuilder;
import dcraft.service.ServiceRequest;
import dcraft.struct.CompositeStruct;
import dcraft.struct.RecordStruct;
import dcraft.task.IWork;
import dcraft.task.IWorkBuilder;

/**
 * Assemble a generic Query request for the database.  A query request should not
 * call a stored procedure that will cause the database to update/alter data
 * (other than temp tables and caches).  Other than that restriction
 * this class can call nearly any stored procedure if the parameters are assembled 
 * correctly.
 * 
 * @author Andy
 *
 */
public class DataRequest implements IServiceRequestBuilder, IWorkBuilder {
	static public DataRequest of(String proc) {
		DataRequest req = new DataRequest(proc);
		return req;
	}
	
	// set by incoming request
	protected String database = "default";
	protected RecordStruct parameters = RecordStruct.record();
	protected String proc = null;
	protected String forTenant = null;
	
	/**
	 * Build an unguided query request.
	 * 
	 * @param proc procedure name to call
	 */
	public DataRequest(String proc) {
		this.proc = proc;
	}
	
	public DataRequest withParam(String field, Object value) {
		this.parameters.with(field, value);
		return this;
	}
	
	public RecordStruct buildParams() {
		this.parameters
			.with("_Database", this.database)
			.with("_ForTenant", this.forTenant);
		
		return this.parameters;
	}
	
	public String getProcedure() {
		return this.proc;
	}
	
	public String getDatabase() {
		return this.database;
	}
	
	public void setDatabase(String v) {
		this.database = v;
	}
	
	public DataRequest withDatabase(String v) {
		this.database = v;
		return this;
	}
	
	public String getForTenant() {
		return this.forTenant;
	}
	
	public void setForTenant(String v) {
		this.forTenant = v;
	}
	
	public DataRequest withForTenant(String v) {
		this.forTenant = v;
		return this;
	}
	
	@Override
	public ServiceRequest toServiceRequest() {
		return this.toDbServiceRequest();
	}

	public DbServiceRequest toDbServiceRequest() {
		return (DbServiceRequest) DbServiceRequest.of(this.proc)
				.withData(this.buildParams());
	}

	/*
	 * this is needed so that the context will be right when response outcome context is set
	 */
	
	@Override
	public IWork toWork() {
		return DbChainWork.of(this);
	}
}
