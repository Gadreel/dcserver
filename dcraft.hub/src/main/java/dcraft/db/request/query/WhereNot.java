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

public class WhereNot extends WhereGroupExpression {
	static public WhereNot not() {
		return new WhereNot();
	}
	
	static public WhereNot of(WhereExpression inner) {
		WhereNot expression = new WhereNot();
		expression.withExpression(inner);
		return expression;
	}
	
	public WhereNot() {
		super("Not");
	}
}
