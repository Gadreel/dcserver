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

import java.time.ZonedDateTime;

import dcraft.struct.Struct;

public class SqlSelectDateTime extends SqlSelect {
	public SqlSelectDateTime(String sql, ZonedDateTime defaultvalue) {
		super(sql, defaultvalue);
	}
	
	public SqlSelectDateTime(String sql, String name, ZonedDateTime defaultvalue) {
		super(sql, name, defaultvalue);
	}

	@Override
	public Object format(Object v) {
		v = Struct.objectToDateTime(v);
		
		if (v != null)
			return v;

		return this.defaultvalue;
	}
}
