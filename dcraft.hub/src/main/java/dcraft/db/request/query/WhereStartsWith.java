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

public class WhereStartsWith extends WhereExpression {
	static public WhereStartsWith starts() {
		return new WhereStartsWith();
	}
	
	static public WhereStartsWith of(String field, Object value) {
		WhereStartsWith expression = new WhereStartsWith();
		expression.withFieldOne(field);
		expression.withValueTwo(value);
		return expression;
	}
	
	public WhereStartsWith() {
		super("StartsWith");
	}
}
