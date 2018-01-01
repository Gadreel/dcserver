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

import dcraft.struct.RecordStruct;
import dcraft.struct.Struct;
import dcraft.util.StringUtil;

/**
 * A database field to filter results in a query.
 * Field may be formated.
 * 
 * @author Andy
 *
 */
public class WhereField implements IWhereField {
	static public WhereField of(String field) {
		WhereField fld = new WhereField();
		fld.withField(field);
		return fld;
	}

	static public WhereField of(String field, String subid) {
		WhereField fld = new WhereField();
		fld.withField(field);
		fld.withSubId(subid);
		return fld;
	}

	protected RecordStruct column = new RecordStruct();
	
	/**
	 * @param field field name
	 */
	public WhereField withField(String field) {
		this.column.with("Field", field);
		return this;
	}
	
	/**
	 * @param subid if field is a list and you wish to match just one value
	 */
	public WhereField withSubId(String subid) {
		this.column.with("SubId", subid);
		return this;
	}
	
	public WhereField withFormat(String format) {
		if (StringUtil.isNotEmpty(format))
			this.column.with("Format", format);
		
		return this;
	}
	
	@Override
	public Struct getParams() {
		return this.column;
	}
	
	@Override
	public String toString() {
		return this.column.toString();
	}
}
