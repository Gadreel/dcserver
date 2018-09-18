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

/**
 * Request that a single database record be loaded.
 * 
 * @author Andy
 *
 */
public class LoadRecordRequest extends DataRequest {
	static public LoadRecordRequest of(String table) {
		LoadRecordRequest request = new LoadRecordRequest();

		request
				.withTable(table);

		return request;
	}

	static public LoadRecordRequest of(String db, String table) {
		LoadRecordRequest request = new LoadRecordRequest();

		request
				.withTable(table)
				.withDatabase(db);

		return request;
	}

	public LoadRecordRequest() {
		super("dcLoadRecord");
	}
	
	public LoadRecordRequest withTable(String v) {
		this.parameters.with("Table", v);
		return this;
	}
	
	public LoadRecordRequest withId(String v) {
		this.parameters.with("Id", v);
		return this;
	}
	
	/*
	public LoadRecordRequest withFilter(String v) {
		((RecordStruct) this.parameters).setField("Filter", v);
		return this;
	}
	*/
	
	public LoadRecordRequest withWhen(BigDateTime v) {
		this.parameters.with("When", v);
		return this;
	}
	
	public LoadRecordRequest withWhen(ZonedDateTime v) {
		this.parameters.with("When", BigDateTime.of(v));
		return this;
	}
	
	public LoadRecordRequest withNow() {
		this.parameters.with("When", BigDateTime.nowDateTime());
		return this;
	}
	
	/*
	public LoadRecordRequest withExtra(Object v) {
		((RecordStruct) this.parameters).setField("Extra", v);
		return this;
	}
	*/
	
	public LoadRecordRequest withSelect(SelectFields v) {
		this.parameters.with("Select", v.getFields());
		return this;
	}
	
	public LoadRecordRequest withCompact(boolean v) {
		this.parameters.with("Compact", v);
		return this;
	}
	
	public LoadRecordRequest withHistorical(boolean v) {
		this.parameters.with("Historical", v);
		return this;
	}
	
	@Override
	public RecordStruct buildParams() {
		// default in When
		if (! this.parameters.hasField("When"))
			this.parameters.with("When", BigDateTime.nowDateTime());
		
		return super.buildParams();
	}
}
