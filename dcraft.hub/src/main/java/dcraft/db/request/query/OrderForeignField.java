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

import dcraft.struct.RecordStruct;
import dcraft.struct.Struct;
import dcraft.util.StringUtil;

/**
 * A database field to order results in a query.
 * Field may be formated and also have a direction.
 * 
 * @author Andy
 *
 */
public class OrderForeignField implements IOrderField {
	protected RecordStruct column = new RecordStruct();
	
	/**
	 * @param field name of foreign key field
	 * @param foreignfield name of foreign field to use for value
	 */
	public OrderForeignField(String field, String foreignfield) {
		this.column.with("Field", field);
		this.column.with("ForeignField", foreignfield);
	}
	
	/**
	 * @param field name of foreign key field
	 * @param foreignfield name of foreign field to use for value
	 * @param direction if this field should be traversed
	 */
	public OrderForeignField(String field, String foreignfield, OrderAs direction) {
		this(field, foreignfield);
		
		if (direction != null)
			this.column.with("Name", direction.toString());
	}
	
	/**
	 * @param field name of foreign key field
	 * @param foreignfield name of foreign field to use for value
	 * @param direction if this field should be traversed
	 * @param format formatting for return value
	 */
	public OrderForeignField(String field, String foreignfield, OrderAs direction, String format) {
		this(field, foreignfield, direction);
		
		if (StringUtil.isNotEmpty(format))
			this.column.with("Format", format);
	}
	
	/* (non-Javadoc)
	 * @see dcraft.db.query.ISelectItem#getSelection()
	 */
	@Override
	public Struct getParams() {
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
