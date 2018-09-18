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
	
	static public WhereTerm of(String field, Object value) {
		WhereTerm expression = new WhereTerm();
		expression.withParam("A", new RecordStruct().with("Value",
				ListStruct.list(WhereField.of(field).getParams()))
		);
		expression.withValueTwo(value);
		return expression;
	}
	
	public WhereTerm() {
		super("Term");
	}
	
	public WhereTerm withFields(String... fields) {
		ListStruct flds = ListStruct.list();
		
		for (String f : fields)
			flds.with(WhereField.of(f).getParams());
		
		this.withParam("A", new RecordStruct().with("Value", flds));
		return this;
	}

	public WhereTerm withFields(WhereField... fields) {
		ListStruct flds = ListStruct.list();

		for (WhereField f : fields)
			flds.with(f.getParams());

		this.withParam("A", new RecordStruct().with("Value", flds));
		return this;
	}
}
