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

public class WhereAnd extends WhereGroupExpression {
	static public WhereAnd and() {
		return new WhereAnd();
	}
	
	static public WhereAnd of(WhereExpression... list) {
		WhereAnd expression = new WhereAnd();
		expression.withAll(list);
		return expression;
	}
	
	public WhereAnd() {
		super("And");
	}
}
