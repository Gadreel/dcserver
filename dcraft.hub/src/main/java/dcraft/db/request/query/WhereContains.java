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

public class WhereContains extends WhereExpression {
	static public WhereContains contains() {
		return new WhereContains();
	}
	
	static public WhereContains of(String field, Object value) {
		WhereContains expression = new WhereContains();
		expression.withFieldOne(field);
		expression.withValueTwo(value);
		return expression;
	}
	
	public WhereContains() {
		super("Contains");
	}
}
