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

public class WhereLessThanOrEqual extends WhereExpression {
	static public WhereLessThanOrEqual lessThanOrEqual() {
		return new WhereLessThanOrEqual();
	}
	
	static public WhereLessThanOrEqual of(String field, Object value) {
		WhereLessThanOrEqual expression = new WhereLessThanOrEqual();
		expression.withFieldOne(field);
		expression.withValueTwo(value);
		return expression;
	}
	
	public WhereLessThanOrEqual() {
		super("LessThanOrEqual");
	}
}
