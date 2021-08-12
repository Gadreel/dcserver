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
import dcraft.struct.CompositeStruct;
import dcraft.struct.RecordStruct;

public class SelectDirectRequest extends DataRequest {
	static public SelectDirectRequest of(String table) {
		SelectDirectRequest request = new SelectDirectRequest();

		request
				.withTable(table);

		return request;
	}

	static public SelectDirectRequest of(String db, String table) {
		SelectDirectRequest request = new SelectDirectRequest();

		request
				.withTable(table)
				.withDatabase(db);

		return request;
	}

	public SelectDirectRequest() {
		super("dcSelectDirect");
	}
	
	public SelectDirectRequest withTable(String v) {
		this.withParam("Table", v);
		return this;
	}
	
	public SelectDirectRequest withId(String v) {
		this.withParam("Id", v);
		return this;
	}
	
	public SelectDirectRequest withWhere(WhereExpression v) {
		this.withParam("Where", v.getParams());
		return this;
	}
	
	public SelectDirectRequest withCollector(ICollector v) {
		this.withParam("Collector", v.getParams());
		return this;
	}

	public SelectDirectRequest withSelect(SelectFields v) {
		this.withParam("Select", v.getFields());
		return this;
	}
	
	public SelectDirectRequest withCompact(boolean v) {
		this.withParam("Compact", v);
		return this;
	}
}
