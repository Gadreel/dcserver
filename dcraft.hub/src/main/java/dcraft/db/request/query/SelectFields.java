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

/**
 * This is a collection of database fields to be selected by a query.  A selected
 * field may be formated and also may hold a subquery.
 * 
 * @author Andy
 *
 */
public class SelectFields {
	static public SelectFields select() {
		return new SelectFields();
	}

	static public SelectFields of(ISelectField... items) {
		SelectFields fields = new SelectFields();
		fields.addField(items);
		return fields;
	}

	protected ListStruct fields = new ListStruct();
	
	/**
	 * @return the selected fields (uses an internal format)
	 */
	public ListStruct getFields() {
		return this.fields;
	}

	public void addField(ISelectField... items) {
		if (items != null)
			for (ISelectField itm : items)
				this.fields.withItem(itm.getParams());
	}
	
	@Override
	public String toString() {
		return this.fields.toString();
	}
		
	public SelectFields withSelect(ISelectField... items) {
		if (items != null)
			for (ISelectField itm : items)
				this.fields.withItem(itm.getParams());
		
		return this;
	}
	
	/**
	 * @param field name of foreign key field
	 */
	public SelectFields with(String field) {
		SelectField sub = new SelectField()
			.with(field);
		
		this.fields.withItem(sub.getParams());
		
		return this;
	}
	
	/**
	 * @param field name of foreign key field
	 * @param name display (return) name
	 */
	public SelectFields with(String field, String name) {
		SelectField sub = new SelectField()
			.with(field)
			.withName(name);
		
		this.fields.withItem(sub.getParams());
		
		return this;
	}
	
	public SelectFields withSubField(String field, String subid, String name) {
		SelectField sub = new SelectField()
			.with(field)
			.withSubId(subid)
			.withName(name);
		
		this.fields.withItem(sub.getParams());
		
		return this;
	}
	
	/**
	 * @param field name of foreign key field
	 * @param name display (return) name
	 * @param format formatting for return value
	 */
	public SelectFields with(String field, String name, String format) {
		SelectField sub = new SelectField()
			.with(field)
			.withName(name)
			.withFormat(format);
		
		this.fields.withItem(sub.getParams());
		
		return this;
	}
	
	public SelectFields with(String field, String name, String format, boolean full) {
		SelectField sub = new SelectField()
			.with(field)
			.withName(name)
			.withFormat(format)
			.withFull(full);
		
		this.fields.withItem(sub.getParams());
		
		return this;
	}
	
	/**
	 * @param field foreign key field name
	 * @param items/subqueries to use as initial values for select
	 */
	public SelectFields withSubquery(String field, ISelectField... items) {
		SelectSubquery sub = new SelectSubquery()
			.with(field)
			.withSelect(items);
		
		this.fields.withItem(sub.getParams());
		
		return this;
	}
	
	public SelectFields withSubquery(String field, SelectFields flds) {
		SelectSubquery sub = new SelectSubquery()
				.with(field)
				.withSelect(flds);
		
		this.fields.withItem(sub.getParams());
		
		return this;
	}
	
	/**
	 * @param field foreign key field name
	 * @param name display name
	 * @param items/subqueries to use as initial values for select
	 */
	public SelectFields withSubquery(String field, String name, ISelectField... items) {
		SelectSubquery sub = new SelectSubquery()
			.withName(name)
			.with(field)
			.withSelect(items);
		
		this.fields.withItem(sub.getParams());
		
		return this;
	}	
	
	public SelectFields withSubquery(String field, String name, SelectFields flds) {
		SelectSubquery sub = new SelectSubquery()
			.withName(name)
			.with(field)
			.withSelect(flds);
		
		this.fields.withItem(sub.getParams());
		
		return this;
	}	
	
	/**
	 * @param field name of foreign key field
	 * @param name display (return) name
	 * @param foreignfield name of foreign field to use for value
	 */
	public SelectFields withForeignField(String field, String name, String foreignfield) {
		SelectForeignField sub = new SelectForeignField()
			.with(field)
			.withName(name)
			.withForeignField(foreignfield);
		
		this.fields.withItem(sub.getParams());
		
		return this;
	}
	
	/**
	 * @param field name of foreign key field
	 * @param name display (return) name
	 * @param foreignfield name of foreign field to use for value
	 * @param format formatting for return value
	 */
	public SelectFields withForeignField(String field, String name, String foreignfield, String format) {
		SelectForeignField sub = new SelectForeignField()
			.with(field)
			.withName(name)
			.withForeignField(foreignfield)
			.withFormat(format);
		
		this.fields.withItem(sub.getParams());
		
		return this;
	}
	
	/**
	 * @param name display (return) name
	 * @param table foreign table
	 * @param keyfield name of foreign field to use for id lookup
	 * @param foreignfield name of foreign field to use for value
	 */
	public SelectFields withReverseForeignField(String name, String table, String keyfield, String foreignfield) {
		SelectReverseForeignField sub = new SelectReverseForeignField()
			.withField("Id")		// doesn't really mean anything
			.withName(name)
			.withTable(table)
			.withKeyField(keyfield)
			.withForeignField(foreignfield);
		
		this.fields.withItem(sub.getParams());
		
		return this;
	}
	
	/**
	 * @param name display (return) name
	 * @param table foreign table
	 * @param keyfield name of foreign field to use for id lookup
	 * @param foreignfield name of foreign field to use for value
	 * @param format formatting for return value
	 */
	public SelectFields withReverseForeignField(String name, String table, String keyfield, String foreignfield, String format) {
		SelectReverseForeignField sub = new SelectReverseForeignField()
			.withField("Id")		// doesn't really mean anything
			.withName(name)
			.withTable(table)
			.withKeyField(keyfield)
			.withForeignField(foreignfield)
			.withFormat(format);
		
		this.fields.withItem(sub.getParams());
		
		return this;
	}
	
	public SelectFields withReverseSubquery(String name, String table, String keyfield, ISelectField... items) {
		SelectReverseSubquery sub = new SelectReverseSubquery()
			.with("Id")		// doesn't really mean anything
			.withName(name)
			.withTable(table)
			.withKeyField(keyfield)
			.withSelect(items);
		
		this.fields.withItem(sub.getParams());
		
		return this;
	}	
	
	public SelectFields withReverseSubquery(String name, String table, String keyfield, SelectFields flds) {
		SelectReverseSubquery sub = new SelectReverseSubquery()
			.with("Id")		// doesn't really mean anything
			.withName(name)
			.withTable(table)
			.withKeyField(keyfield)
			.withSelect(flds);
		
		this.fields.withItem(sub.getParams());
		
		return this;
	}	
	
	/**
	 * @param composer function to compose response
	 * @param name display (return) name
	 */
	public SelectFields withComposer(String composer, String name) {
		SelectComposer sub = new SelectComposer()
			.withComposer(composer)
			.withName(name);
		
		this.fields.withItem(sub.getParams());
		
		return this;
	}
	
	/**
	 * @param composer function to compose response
	 * @param name display (return) name
	 */
	public SelectFields withComposer(String composer, String name, RecordStruct params) {
		SelectComposer sub = new SelectComposer()
			.withComposer(composer)
			.withName(name)
			.withParams(params);
		
		this.fields.withItem(sub.getParams());
		
		return this;
	}
	
	/**
	 * @param composer function to compose response
	 * @param name display (return) name
	 * @param format formatting for return value
	 */
	public SelectFields withComposer(String composer, String name, String format) {
		SelectComposer sub = new SelectComposer()
			.withComposer(composer)
			.withName(name)
			.withFormat(format);
		
		this.fields.withItem(sub.getParams());
		
		return this;
	}
}
