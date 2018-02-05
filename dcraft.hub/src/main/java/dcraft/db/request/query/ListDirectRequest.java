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

public class ListDirectRequest extends DataRequest {
	public void setHistorical(boolean v) {
		this.parameters.with("Historical", v);
	}
	
	public ListDirectRequest(String table, ISelectField select) {
		this(table, select, null, null, (BigDateTime)null);
	}
	
	public ListDirectRequest(String table, ISelectField select, WhereExpression where) {
		this(table, select, where, null, (BigDateTime)null);
	}
	
	public ListDirectRequest(String table, ISelectField select, WhereExpression where, ICollector collector) {
		this(table, select, where, collector, (BigDateTime)null);
	}
	
	public ListDirectRequest(String table, ISelectField select, WhereExpression where, BigDateTime when) {
		this(table, select, where, null, when);
	}
	
	public ListDirectRequest(String table, ISelectField select, WhereExpression where, ZonedDateTime when) {
		this(table, select, where, null, (when != null) ? BigDateTime.of(when) : null);
	}
	
	public ListDirectRequest(String table, ISelectField select, WhereExpression where, ICollector collector, ZonedDateTime when) {
		this(table, select, where, collector, (when != null) ? BigDateTime.of(when) : null);
	}
	
	public ListDirectRequest(String table, ISelectField select, WhereExpression where, ICollector collector, BigDateTime when) {
		super("dcListDirect");

		this.parameters
			.with("Table", table);
		
		if (select != null)
			this.parameters.with("Select", select.getParams());
		
		if (where != null)
			this.parameters.with("Where", where.getParams());
		
		if (collector != null)
			this.parameters.with("Collector", collector.getParams());
		
		if (when != null)
			this.parameters.with("When", when);
		else
			this.parameters.with("When", BigDateTime.nowDateTime());
	}
}
