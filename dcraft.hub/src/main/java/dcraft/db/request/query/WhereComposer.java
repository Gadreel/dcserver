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
 * A Composer scriptold to generate values to filter on.
 * 
 * @author Andy
 *
 */
public class WhereComposer implements IWhereField {
	protected RecordStruct column = new RecordStruct();
	
	/**
	 * @param composer name of content generating scriptold
	 */
	public WhereComposer(String composer) {
		this.column.with("Composer", composer);
	}
	
	/**
	 * @param composer name of content generating scriptold
	 * @param format formatting for return value
	 */
	public WhereComposer(String composer, String format) {
		this.column.with("Composer", composer);
		
		if (StringUtil.isNotEmpty(format))
			this.column.with("Format", format);
	}
	
	/* (non-Javadoc)
	 * @see IWhereField#getParams()
	 */
	@Override
	public Struct getParams() {
		return this.column;
	}
	
	/* (non-Javadoc)
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString() {
		return this.column.toString();
	}
}
