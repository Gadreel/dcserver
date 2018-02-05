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

public class WhereGreaterThan extends WhereExpression {
	static public WhereGreaterThan greaterThan() {
		return new WhereGreaterThan();
	}
	
	static public WhereGreaterThan of(String field, Object value) {
		WhereGreaterThan expression = new WhereGreaterThan();
		expression.withFieldOne(field);
		expression.withValueTwo(value);
		return expression;
	}
	
	public WhereGreaterThan() {
		super("GreaterThan");
	}
}
