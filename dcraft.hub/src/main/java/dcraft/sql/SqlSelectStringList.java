/* ************************************************************************
#
#  designCraft.io
#
#  http://designcraft.io/
#
#  Copyright:
#    Copyright 2014 eTimeline, LLC. All rights reserved.
#
#  License:
#    See the license.txt file in the project's top-level directory for details.
#
#  Authors:
#    * Andy White
#
************************************************************************ */
package dcraft.sql;

import dcraft.struct.ListStruct;
import dcraft.struct.Struct;

public class SqlSelectStringList extends SqlSelect {
	protected String delineator = ",";
	
	public void setDelineator(String v) {
		this.delineator = v;
	}
	
	public SqlSelectStringList(String sql, String defaultvalue) {
		super(sql, (Object)defaultvalue);
	}
	
	public SqlSelectStringList(String sql, String name, String defaultvalue) {
		super(sql, name, defaultvalue);
	}

	@Override
	public Object format(Object v) {
		v = Struct.objectToString(v);
		
		if (v == null)
			v = this.defaultvalue;

		if (v == null)
			return null;
		
		return ListStruct.list((Object[]) ((String)v).split(this.delineator));
	}
}
