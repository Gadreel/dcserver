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

public class WhereIn extends WhereExpression {
	static public WhereIn in() {
		return new WhereIn();
	}
	
	static public WhereIn of(String field, Object value) {
		WhereIn expression = new WhereIn();
		expression.withFieldOne(field);
		expression.withValueTwo(value);
		return expression;
	}
	
	public WhereIn() {
		super("In");
	}
}
