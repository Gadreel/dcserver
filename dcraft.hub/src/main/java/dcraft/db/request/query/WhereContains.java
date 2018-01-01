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

public class WhereContains extends WhereExpression {
	static public WhereContains contains() {
		return new WhereContains();
	}
	
	public WhereContains() {
		super("Contains");
	}
	
	public WhereContains withField(String name) {
		return this.withField(WhereField.of(name));
	}
	
	public WhereContains withField(IWhereField fld) {
		if (! this.params.hasField("A"))
			this.addField("A", fld);
		else if (! this.params.hasField("B"))
			this.addField("B", fld);
		
		return this;
	}
	
	public WhereContains withValue(Object v) {
		if (! this.params.hasField("A"))
			this.addValue("A", v);
		else if (! this.params.hasField("B"))
			this.addValue("B", v);
		
		return this;
	}
}
