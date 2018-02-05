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

public class WhereEqual extends WhereExpression {
	static public WhereEqual equal() {
		return new WhereEqual();
	}
	
	static public WhereEqual of(String field, Object value) {
		WhereEqual expression = new WhereEqual();
		expression.withFieldOne(field);
		expression.withValueTwo(value);
		return expression;
	}

	public WhereEqual() {
		super("Equal");
	}
}
