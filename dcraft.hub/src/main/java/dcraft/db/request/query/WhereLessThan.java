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

public class WhereLessThan extends WhereExpression {
	static public WhereLessThan lessThan() {
		return new WhereLessThan();
	}
	
	static public WhereLessThan of(String field, Object value) {
		WhereLessThan expression = new WhereLessThan();
		expression.withFieldOne(field);
		expression.withValueTwo(value);
		return expression;
	}
	
	public WhereLessThan() {
		super("LessThan");
	}
}
