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

public class WhereRange extends WhereExpression {
	public WhereRange(Object a, Object b, Object c) {
		super("Range");
		
		this.addValue("A", a);
		this.addValue("B", b);
		this.addValue("C", c);
	}
	
	public WhereRange(IWhereField a, Object b, Object c) {
		super("Range");
		
		this.addField("A", a);
		this.addValue("B", b);
		this.addValue("C", c);
	}
	
	public WhereRange(Object a, IWhereField b, Object c) {
		super("Range");
		
		this.addValue("A", a);
		this.addField("B", b);
		this.addValue("C", c);
	}
	
	public WhereRange(Object a, Object b, IWhereField c) {
		super("Range");
		
		this.addValue("A", a);
		this.addValue("B", b);
		this.addField("C", c);
	}
		
	public WhereRange(IWhereField a, Object b, IWhereField c) {
		super("Range");
		
		this.addField("A", a);
		this.addValue("B", b);
		this.addField("C", c);
	}
	
	public WhereRange(IWhereField a, IWhereField b, Object c) {
		super("Range");
		
		this.addField("A", a);
		this.addField("B", b);
		this.addValue("C", c);
	}
	
	public WhereRange(Object a, IWhereField b, IWhereField c) {
		super("Range");
		
		this.addValue("A", a);
		this.addField("B", b);
		this.addField("C", c);
	}
	
	public WhereRange(IWhereField a, IWhereField b, IWhereField c) {
		super("Range");
		
		this.addField("A", a);
		this.addField("B", b);
		this.addField("C", c);
	}
}
