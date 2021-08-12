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

public class ListRequest extends DataRequest {
	public long getOffset() {
		return this.parameters.hasField("Offset") ? this.parameters.getFieldAsInteger("Offset") : 0;
	}
	
	public void setOffset(long v) {
		this.parameters.with("Offset", v);
	}
	
	public long getPageSize() {
		return this.parameters.hasField("PageSize") ? this.parameters.getFieldAsInteger("PageSize") : 100;
	}
	
	public void setPageSize(long v) {
		this.parameters.with("PageSize", v);
	}
	
	public boolean isCacheEnabled() {
		return this.parameters.hasField("CacheEnabled") ? this.parameters.getFieldAsBoolean("CacheEnabled") : false;
	}
	
	public void setCacheEnabled(boolean v) {
		this.parameters.with("CacheEnabled", v);
	}
	
	public long getCacheId() {
		return this.parameters.hasField("CacheId") ? this.parameters.getFieldAsInteger("CacheId") : null;
	}
	
	public void setCacheId(long v) {
		this.parameters.with("CacheEnabled", true);
		this.parameters.with("CacheId", v);
	}
	

	public ListRequest(String table, ISelectField select, OrderFields order, WhereExpression where, ICollector collector) {
		super("dcList");
		
		this.parameters.with("Table", table);
		
		if (select != null)
			this.parameters.with("Select", select.getParams());
		
		if (order != null)
			this.parameters.with("Order", order.getFields());
		
		if (where != null)
			this.parameters.with("Where", where.getParams());
		
		if (collector != null)
			this.parameters.with("Collector", collector.getParams());
	}
	
	public void nextPage() {
		if (!this.isCacheEnabled())
			return;
		
		long newoffset = this.getOffset() + this.getPageSize();
		this.setOffset(newoffset);
	}
			
	public void prevPage() {
		if (!this.isCacheEnabled())
			return;
		
		long newoffset = this.getOffset() - this.getPageSize();
		
		if (newoffset < 0)
			newoffset = 0;
		
		this.setOffset(newoffset);
	}
}
