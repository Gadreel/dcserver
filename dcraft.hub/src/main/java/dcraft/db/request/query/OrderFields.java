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

/**
 * This is a collection of database fields that tell dcDb how to order the results.
 * 
 * @author Andy
 *
 */
public class OrderFields {
	protected ListStruct fields = new ListStruct();
	
	/**
	 * @return the order fields (uses an internal format)
	 */
	public ListStruct getFields() {
		return this.fields;
	}
	
	/**
	 * @param fields/subqueries to use as initial values for order by
	 */
	public OrderFields(IOrderField... items) {
		this.addField(items);
	}	
	
	/**
	 * @param fields/subqueries to add to the order by
	 */
	public void addField(IOrderField... items) {
		if (items != null)
			for (IOrderField itm : items)
				this.fields.withItem(itm.getParams());
	}
	
	/* (non-Javadoc)
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString() {
		return this.fields.toString();
	}
}
