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

public class WhereOr extends WhereGroupExpression {
	static public WhereOr or() {
		return new WhereOr();
	}
	
	static public WhereOr of(WhereExpression... list) {
		WhereOr expression = new WhereOr();
		expression.withAll(list);
		return expression;
	}
	
	public WhereOr(WhereExpression... list) {
		super("Or");
	}
}
