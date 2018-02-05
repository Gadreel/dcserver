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

public class WhereIsNot extends WhereExpression {
	static public WhereIsNot isNot() {
		return new WhereIsNot();
	}
	
	static public WhereIsNot of(String field) {
		WhereIsNot expression = new WhereIsNot();
		expression.withFieldOne(field);
		return expression;
	}
	
	static public WhereIsNot of(String field, String subid) {
		WhereIsNot expression = new WhereIsNot();
		expression.withFieldOne(field, subid);
		return expression;
	}
	
	public WhereIsNot() {
		super("IsNot");
	}
}
