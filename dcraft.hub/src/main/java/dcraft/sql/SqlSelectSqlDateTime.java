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
import dcraft.util.TimeUtil;

public class SqlSelectSqlDateTime extends SqlSelect {
	protected boolean sqlFmt = false;
	
	public SqlSelectSqlDateTime(String sql, ZonedDateTime defaultvalue) {
		super(sql, defaultvalue);
	}
	
	public SqlSelectSqlDateTime(String sql, ZonedDateTime defaultvalue, boolean sqlFmt) {
		super(sql, defaultvalue);
		this.sqlFmt = sqlFmt;
	}
	
	public SqlSelectSqlDateTime(String sql, String name, ZonedDateTime defaultvalue) {		
		super(sql, name, defaultvalue);
	}
	
	public SqlSelectSqlDateTime(String sql, String name, ZonedDateTime defaultvalue, boolean sqlFmt) {		
		super(sql, name, defaultvalue);
		this.sqlFmt = sqlFmt;
	}

	@Override
	public Object format(Object v) {
		ZonedDateTime tv = Struct.objectToDateTime(v);
		
		if (tv != null)
			return this.sqlFmt ? TimeUtil.sqlStampFmt.format(tv) : tv;   
		
		return this.defaultvalue;		
	}
}
