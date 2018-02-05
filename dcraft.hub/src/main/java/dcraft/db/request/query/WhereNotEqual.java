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

public class WhereNotEqual extends WhereExpression {
	static public WhereNotEqual notEqual() {
		return new WhereNotEqual();
	}
	
	static public WhereNotEqual of(String field, Object value) {
		WhereNotEqual expression = new WhereNotEqual();
		expression.withFieldOne(field);
		expression.withValueTwo(value);
		return expression;
	}
	
	public WhereNotEqual() {
		super("NotEqual");
	}
}
