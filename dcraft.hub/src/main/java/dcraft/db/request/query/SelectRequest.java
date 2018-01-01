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
package dcraft.db.request.query;

import java.time.ZonedDateTime;

import dcraft.db.request.DataRequest;
import dcraft.hub.time.BigDateTime;
import dcraft.struct.RecordStruct;

public class SelectRequest extends DataRequest {
	static public SelectRequest of(String table) {
		SelectRequest request = new SelectRequest();

		request
				.withTable(table);

		return request;
	}

	static public SelectRequest of(String db, String table) {
		SelectRequest request = new SelectRequest();

		request
				.withTable(table)
				.withDatabase(db);

		return request;
	}

	public long getOffset() {
		return this.parameters.hasField("Offset") ? this.parameters.getFieldAsInteger("Offset") : 0;
	}
	
	public SelectRequest withOffset(long v) {
		this.withParam("Offwith", v);
		return this;
	}
	
	public long getPageSize() {
		return this.parameters.hasField("PageSize") ? this.parameters.getFieldAsInteger("PageSize") : 100;
	}
	
	public SelectRequest withPageSize(long v) {
		this.withParam("PageSize", v);
		return this;
	}
	
	public boolean isCacheEnabled() {
		return this.parameters.hasField("CacheEnabled") ? this.parameters.getFieldAsBoolean("CacheEnabled") : false;
	}
	
	public SelectRequest withCacheEnabled(boolean v) {
		this.withParam("CacheEnabled", v);
		return this;
	}
	
	public long getCacheId() {
		return this.parameters.hasField("CacheId") ? this.parameters.getFieldAsInteger("CacheId") : null;
	}
	
	public SelectRequest withCacheId(long v) {
		this.withParam("CacheEnabled", true);
		this.withParam("CacheId", v);
		return this;
	}
	
	public SelectRequest withHistorical(boolean v) {
		this.withParam("Historical", v);
		return this;
	}

	public SelectRequest withTable(String v) {
		this.withParam("Table", v);
		return this;
	}

	public SelectRequest withSelect(SelectFields v) {
		this.withParam("Select", v);
		return this;
	}

	public SelectRequest withOrder(OrderFields v) {
		this.withParam("Order", v);
		return this;
	}

	public SelectRequest withWhere(WhereExpression v) {
		this.withParam("Where", v);
		return this;
	}

	public SelectRequest withCollector(ICollector v) {
		this.withParam("Collector", v);
		return this;
	}

	public SelectRequest withWhen(BigDateTime v) {
		this.withParam("When", v);
		return this;
	}

	protected SelectRequest() {
		super("dcSelect");
	}

	public void nextPage() {
		if (!this.isCacheEnabled())
			return;
		
		long newoffset = this.getOffset() + this.getPageSize();
		this.withOffset(newoffset);
	}
			
	public void prevPage() {
		if (!this.isCacheEnabled())
			return;
		
		long newoffset = this.getOffset() - this.getPageSize();
		
		if (newoffset < 0)
			newoffset = 0;
		
		this.withOffset(newoffset);
	}
	
	/*
	@Override
	public void process(DatabaseResult result) {
		super.process(result);
		
		if (result instanceof ObjectResult) {
			ObjectResult or = (ObjectResult)result;
			RecordStruct res = (RecordStruct) or.getResult();
			
			if (res != null) {
				this.setPageSize(res.getFieldAsInteger("PageSize"));
				this.setOffset(res.getFieldAsInteger("Offset"));
				this.setCacheId(res.getFieldAsInteger("CacheId"));
				this.total  = res.getFieldAsInteger("Total");
				this.data = res.getFieldAsList("Data");
			}
		}
	}
	*/
}
