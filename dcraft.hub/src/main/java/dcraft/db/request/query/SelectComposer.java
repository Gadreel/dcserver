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

import dcraft.struct.BaseStruct;
import dcraft.struct.RecordStruct;
import dcraft.struct.Struct;

/**
 * A Composer scriptold to generate content in a query.
 * 
 * @author Andy
 *
 */
public class SelectComposer implements ISelectField {
	protected RecordStruct column = new RecordStruct();
	
	public SelectComposer withComposer(String v) {
		this.column.with("Composer", v);		
		return this;
	}
	
	public SelectComposer withFilter(String v) {
		this.column.with("Filter", v);
		return this;
	}
	
	public SelectComposer withName(String v) {
		this.column.with("Name", v);		
		return this;
	}
	
	public SelectComposer withFormat(String v) {
		this.column.with("Format", v);		
		return this;
	}
	
	public SelectComposer with(String v) {
		this.column.with("Field", v);		
		return this;
	}
	
	public SelectComposer withParams(RecordStruct v) {
		this.column.with("Params", v);		
		return this;
	}

	@Override
	public BaseStruct getParams() {
		return this.column;
	}
	
	@Override
	public String toString() {
		return this.column.toString();
	}
}
