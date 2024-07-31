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

public class WhereIsEmpty extends WhereExpression {
	static public WhereIsEmpty isNot() {
		return new WhereIsEmpty();
	}

	static public WhereIsEmpty of(String field) {
		WhereIsEmpty expression = new WhereIsEmpty();
		expression.withFieldOne(field);
		return expression;
	}

	static public WhereIsEmpty of(String field, String subid) {
		WhereIsEmpty expression = new WhereIsEmpty();
		expression.withFieldOne(field, subid);
		return expression;
	}

	public WhereIsEmpty() {
		super("IsEmpty");
	}
}
