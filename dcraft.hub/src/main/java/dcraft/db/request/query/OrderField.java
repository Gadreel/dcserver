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
import dcraft.util.StringUtil;

/**
 * A database field to order by in a query.
 * Field may be formated and a direction.
 * 
 * @author Andy
 *
 */
public class OrderField implements IOrderField {
	protected RecordStruct column = new RecordStruct();
	
	/**
	 * @param field field name
	 */
	public OrderField(String field) {
		this.column.with("Field", field);
	}
	
	/**
	 * @param field field name
	 * @param direction if this field should be traversed
	 */
	public OrderField(String field, OrderAs direction) {
		this(field);
		
		if (direction != null)
			this.column.with("Direction", direction.toString());
	}
	
	/**
	 * @param field field name
	 * @param direction if this field should be traversed
	 * @param format formatting for return value
	 */
	public OrderField(String field, OrderAs direction, String format) {
		this(field, direction);
		
		if (StringUtil.isNotEmpty(format))
			this.column.with("Format", format);
	}
	
	/* (non-Javadoc)
	 * @see dcraft.db.query.ISelectItem#getSelection()
	 */
	@Override
	public BaseStruct getParams() {
		return this.column;
	}
	
	/* (non-Javadoc)
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString() {
		return this.column.toString();
	}
}
