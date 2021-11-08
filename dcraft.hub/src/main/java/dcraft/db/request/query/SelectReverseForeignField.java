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

import dcraft.struct.BaseStruct;
import dcraft.struct.RecordStruct;
import dcraft.struct.Struct;

/**
 * A database field to select in a query.
 * Field may be formated and also may hold a display name.
 * 
 * @author Andy
 *
 */
public class SelectReverseForeignField implements ISelectField {
	protected RecordStruct column = new RecordStruct();
	
	public SelectReverseForeignField withField(String v) {
		this.column.with("Field", v);
		return this;
	}
	
	public SelectReverseForeignField withName(String v) {
		this.column.with("Name", v);
		return this;
	}
	
	public SelectReverseForeignField withTable(String v) {
		this.column.with("Table", v);
		return this;
	}
	
	public SelectReverseForeignField withKeyField(String v) {
		this.column.with("KeyField", v);
		return this;
	}
	
	public SelectReverseForeignField withForeignField(String v) {
		this.column.with("ForeignField", v);
		return this;
	}
	
	public SelectReverseForeignField withFormat(String v) {
		this.column.with("Format", v);
		return this;
	}
	
	@Override
	public BaseStruct getParams() {
		return this.column;
	}
	
	@Override
	public String toString() {
		return this.column.toString();
	}
}
