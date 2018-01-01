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
import dcraft.struct.RecordStruct;

public class WhereTerm extends WhereExpression {
	static public WhereTerm term() {
		return new WhereTerm();
	}
	
	public WhereTerm() {
		super("Term");
	}
	
	public WhereTerm withField(String name) {
		return this.withField(WhereField.of(name));
	}
	
	public WhereTerm withField(IWhereField fld) {
		this.params.with("A", RecordStruct.record().with("Value", ListStruct.list(fld.getParams())));
		return this;
	}
	
	public WhereTerm withValue(Object v) {
		this.addValue("B", v);
		return this;
	}
}
