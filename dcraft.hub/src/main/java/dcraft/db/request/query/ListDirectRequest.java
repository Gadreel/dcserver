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
	public ListDirectRequest(String table, ISelectField select) {
		this(table, select, null, null);
	}
	
	public ListDirectRequest(String table, ISelectField select, WhereExpression where) {
		this(table, select, where, null);
	}
	
	public ListDirectRequest(String table, ISelectField select, WhereExpression where, ICollector collector) {
		super("dcListDirect");

		this.parameters
			.with("Table", table);
		
		if (select != null)
			this.parameters.with("Select", select.getParams());
		
		if (where != null)
			this.parameters.with("Where", where.getParams());
		
		if (collector != null)
			this.parameters.with("Collector", collector.getParams());
	}
}
