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

public class WhereIs extends WhereExpression {
	static public WhereIs is() {
		return new WhereIs();
	}
	
	static public WhereIs of(String field) {
		WhereIs expression = new WhereIs();
		expression.withFieldOne(field);
		return expression;
	}
	
	static public WhereIs of(String field, String subid) {
		WhereIs expression = new WhereIs();
		expression.withFieldOne(field, subid);
		return expression;
	}
	
	public WhereIs() {
		super("Is");
	}
}
