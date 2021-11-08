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
import dcraft.struct.ListStruct;
import dcraft.struct.RecordStruct;
import dcraft.struct.Struct;

/**
 * Names the collector procedure to use to provide record ids to a Select or List request.  
 * 
 * @author Andy
 *
 */
public class CollectorFunc implements ICollector {
	protected RecordStruct column = new RecordStruct();
	
	/**
	 * @param name of id looping scriptold
	 */
	public CollectorFunc(String name) {
		this.column.with("Func", name);
	}
	
	public CollectorFunc withValues(Object... values) {
		if (values != null) {
			ListStruct list = new ListStruct();
			
			for (Object v : values)
				list.withItem(v);
			
			this.column.with("Values", list);
		}
		
		return this;
	}
	
	public CollectorFunc withValues(ListStruct values) {
		this.column.with("Values", values);
		
		return this;
	}
	
	public CollectorFunc withExtra(RecordStruct v) {
		this.column.with("Extras", v);
		
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
