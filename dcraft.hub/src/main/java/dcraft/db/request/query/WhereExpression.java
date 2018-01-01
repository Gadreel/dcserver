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

import dcraft.struct.CompositeStruct;
import dcraft.struct.RecordStruct;

abstract public class WhereExpression {
	protected RecordStruct params = new RecordStruct();
	
	public WhereExpression(String name) {
		this.params.with("Expression", name);
	}
	
	public void addValue(String part, Object v) {
		this.params.with(part, new RecordStruct().with("Value", v));
	}
	
	public void addField(String part, IWhereField v) {
		if (v != null)
			this.params.with(part, v.getParams());
	}
	
	public CompositeStruct getFields() {
		return this.params;
	}
}
