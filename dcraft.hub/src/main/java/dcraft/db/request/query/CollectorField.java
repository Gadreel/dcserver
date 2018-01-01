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

import dcraft.struct.ListStruct;
import dcraft.struct.RecordStruct;
import dcraft.struct.Struct;

/**
 * Names the indexed field to use to provide record ids to a Select or List request.  
 * 
 * @author Andy
 *
 */
public class CollectorField implements ICollector {
	static public CollectorField collect() {
		return new CollectorField();
	}
	
	protected RecordStruct column = new RecordStruct();
	
	public CollectorField() { }
	
	public CollectorField withField(String name) {
		this.column.with("Field", name);
		return this;
	}
	
	
	public CollectorField withValues(Object... values) {
		if (values != null) {
			ListStruct list = new ListStruct();
			
			for (Object v : values)
				list.withItem(v);
			
			this.column.with("Values", list);
		}
		
		return this;
	}
	
	public CollectorField withValues(ListStruct values) {
		this.column.with("Values", values);
		
		return this;
	}
	
	public CollectorField withExtra(RecordStruct v) {
		this.column.with("Extras", v);
		
		return this;
	}
	
	public CollectorField withFrom(Object v) {
		this.column.with("From", v);
		
		return this;
	}
	
	public CollectorField withTo(Object v) {
		this.column.with("To", v);
		
		return this;
	}
	
	public CollectorField withSubId(Object v) {
		this.column.with("SubId", v);
		
		return this;
	}
	
	@Override
	public Struct getParams() {
		return this.column;
	}
	
	@Override
	public String toString() {
		return this.column.toString();
	}
}
