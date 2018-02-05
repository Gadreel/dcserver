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

public class WhereRange extends WhereExpression {
	static public WhereRange range() {
		return new WhereRange();
	}
	
	static public WhereRange of(String field, Object from, Object to) {
		WhereRange expression = new WhereRange();
		expression.withFieldOne(field);
		expression.withValueTwo(from);
		expression.withValueThree(to);
		return expression;
	}
	
	public WhereRange() {
		super("Range");
	}
}
