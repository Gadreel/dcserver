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
package dcraft.schema;

import dcraft.util.StringUtil;
import dcraft.xml.XElement;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TableView {
	static public TableView table(String name) {
		TableView table = new TableView();
		table.name = name;
		return table;
	}
	
	static public TableView table(DbTable def) {
		TableView table = new TableView();
		table.name = def.name;
		table.fields.putAll(def.fields);
		return table;
	}
	
	protected String name = null;
	protected Map<String, DbField> fields = new HashMap<>();

	public String getName() {
		return this.name;
	}
	
	public DbField getField(String name) {
		return this.fields.get(name);
	}
	
	public List<DbField> getFields() {
		return new ArrayList<>(this.fields.values());
	}
	
	/*
	 * Use only during a table lookup when attempting to return a single unified view of a table
	 * with all its fields. Do not use within schema cache.
	 */
	public TableView mergeWith(DbTable table) {
		this.fields.putAll(table.fields);
		return this;
	}
}
