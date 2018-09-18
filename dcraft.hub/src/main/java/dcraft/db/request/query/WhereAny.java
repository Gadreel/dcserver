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

import dcraft.struct.ListStruct;

public class WhereAny extends WhereExpression {
	static public WhereAny any() {
		return new WhereAny();
	}
	
	static public WhereAny of(String field, Object... value) {
		WhereAny expression = new WhereAny();
		expression.withFieldOne(field);
		expression.withValueTwo(ListStruct.list(value));
		return expression;
	}

	static public WhereAny of(String field, ListStruct value) {
		WhereAny expression = new WhereAny();
		expression.withFieldOne(field);
		expression.withValueTwo(value);
		return expression;
	}

	public WhereAny() {
		super("Any");
	}
}
