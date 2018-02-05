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

import dcraft.db.proc.IExpression;
import dcraft.db.proc.expression.ExpressionUtil;
import dcraft.hub.op.OperatingContextException;
import dcraft.struct.CompositeStruct;
import dcraft.struct.RecordStruct;

abstract public class WhereExpression {
	protected RecordStruct params = new RecordStruct();
	
	public WhereExpression(String name) {
		this.params.with("Expression", name);
	}
	
	public WhereExpression withFieldOne(String name) {
		this.params.with("A", WhereField.of(name).getParams());
		return this;
	}
	
	public WhereExpression withFieldOne(String name, String subid) {
		this.params.with("A", WhereField.of(name, subid).getParams());
		return this;
	}
	
	public WhereExpression withFieldOne(IWhereField v) {
		if (v != null)
			this.params.with("A", v.getParams());
		
		return this;
	}
	
	public WhereExpression withValueTwo(Object v) {
		this.params.with("B", new RecordStruct().with("Value", v));
		return this;
	}
	
	public WhereExpression withValueThree(Object v) {
		this.params.with("C", new RecordStruct().with("Value", v));
		return this;
	}
	
	// TODO add support for field Two and Three also
	
	public WhereExpression withParam(String part, Object v) {
		this.params.with(part, v);
		return this;
	}
	
	public WhereExpression withLocale(Object v) {
		this.params.with("Locale", v);
		return this;
	}
	
	public RecordStruct getParams() {
		return this.params;
	}
	
	public IExpression toFilter(String table) throws OperatingContextException {
		return ExpressionUtil.initExpression(table, this.params);
	}
}
