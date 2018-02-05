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

abstract public class WhereGroupExpression extends WhereExpression {
	public WhereGroupExpression(String name) {
		super(name);
	}
	
	public WhereGroupExpression withAll(WhereExpression... list) {
		ListStruct children = ListStruct.list();

		for (WhereExpression expression : list)
			children.with(expression.getParams());

		this.params.with("Children",  children);
		return this;
	}
	
	public WhereGroupExpression withExpression(WhereExpression ex) {
		ListStruct children = this.params.getFieldAsList("Children");
		
		if (children == null) {
			children = ListStruct.list();
			this.params.with("Children", children);
		}
		
		children.with(ex.getParams());
		
		return this;
	}
	
	public int getExpressionCount() {
		return this.params.getFieldAsList("Children").size();
	}
}
