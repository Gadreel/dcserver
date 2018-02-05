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
package dcraft.db.request.update;

import java.util.ArrayList;
import java.util.List;

import dcraft.db.request.DataRequest;
import dcraft.hub.ResourceHub;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
import dcraft.hub.time.BigDateTime;
import dcraft.schema.SchemaResource;
import dcraft.struct.CompositeStruct;
import dcraft.struct.ListStruct;
import dcraft.struct.RecordStruct;
import dcraft.util.StringUtil;

/**
 * Insert a new record into dcDatabase, see dcInsertRecord schema.
 * 
 * @author Andy
 *
 */
abstract public class DbRecordRequest extends DataRequest {
	protected List<FieldRequest> fields = new ArrayList<>();	
	protected ListStruct sets = new ListStruct();
	
	protected String table = null;
	protected String id = null;
	protected BigDateTime when = BigDateTime.nowDateTime();
	
	public DbRecordRequest(String proc) {
		super(proc);
	}
		
	public DbRecordRequest withTable(String v) {
		this.table = v;
		
		this.fields.clear();
		
		return this;
	}
		
	public DbRecordRequest withId(String id) {
		this.id = id;
		return this;
	}

	public DbRecordRequest withFields(FieldRequest... fields) {
		for (FieldRequest fld : fields)
			this.fields.add(fld);		
		
		return this;
	}	

	public DbRecordRequest withSetField(String name, Object value) {
		if (value instanceof ConditionalValue) {
			if (!((ConditionalValue)value).set)
				return this;
			
			value = ((ConditionalValue)value).value;
		}
		
		SchemaResource schema = ResourceHub.getResources().getSchema();
		dcraft.schema.DbField fld = schema.getDbField(this.table, name);
		
		if (fld == null) 
			return this;
		
		FieldRequest dfld = new FieldRequest()
			.withName(name)
			.withValue(value);
		
		this.withFields(dfld);
		
		if (fld.isDynamic())
			dfld.withRandomSubKey().withFrom(this.when);
		else if (fld.isList())		
			dfld.withRandomSubKey();
		
		return this;
	}
	
	public DbRecordRequest withUpdateField(String name, Object value) {
		if (value instanceof ConditionalValue) {
			if (!((ConditionalValue)value).set)
				return this;
			
			value = ((ConditionalValue)value).value;
		}
		
		SchemaResource schema = ResourceHub.getResources().getSchema();
		dcraft.schema.DbField fld = schema.getDbField(this.table, name);
		
		if (fld == null) 
			return this;
		
		FieldRequest dfld = new FieldRequest()
			.withName(name)
			.withValue(value)
			.withUpdateOnly();
		
		this.withFields(dfld);
		
		if (fld.isDynamic())
			dfld.withRandomSubKey().withFrom(this.when);
		else if (fld.isList())		
			dfld.withRandomSubKey();
		
		return this;
	}
	
	public DbRecordRequest withUpdateTrField(String locale, String name, Object value) throws OperatingContextException {
		if (value instanceof ConditionalValue) {
			if (!((ConditionalValue)value).set)
				return this;
			
			value = ((ConditionalValue)value).value;
		}
		
		SchemaResource schema = ResourceHub.getResources().getSchema();
		dcraft.schema.DbField fld = schema.getDbField(this.table, name);
		
		if (fld == null)
			return this;
		
		FieldRequest dfld = new FieldRequest()
				.withName(name)
				.withValue(value)
				.withUpdateOnly();
		
		this.withFields(dfld);
		
		// database uses the Tenant Locale when storing fields, so only Tr those locales that don't match tenant
		if (StringUtil.isNotEmpty(locale) && ! locale.equals(OperationContext.getOrThrow().getTenant().getResources().getLocale().getDefaultLocale())) {
			dfld
					.withName(name + "Tr")
					.withSubKey(locale)
					.withLocale(locale);
		}
		else {
			if (fld.isDynamic())
				dfld.withRandomSubKey().withFrom(this.when);
			
			// Tr fields can only be scalars
			//else if (fld.isList())
			//	dfld.withRandomSubKey();
		}
		
		return this;
	}

	public DbRecordRequest withSetField(String name, String subkey, Object value) {
		if (value instanceof ConditionalValue) {
			if (!((ConditionalValue)value).set)
				return this;
			
			value = ((ConditionalValue)value).value;
		}
		
		SchemaResource schema = ResourceHub.getResources().getSchema();
		dcraft.schema.DbField fld = schema.getDbField(this.table, name);
		
		if (fld == null) 
			return this;
		
		FieldRequest dfld = new FieldRequest()
			.withName(name)
			.withValue(value)
			.withSubKey(subkey);
		
		this.withFields(dfld);
		
		if (fld.isDynamic())
			dfld.withFrom(this.when);
		
		return this;
	}

	public DbRecordRequest withUpdateField(String name, String subkey, Object value) {
		if (value instanceof ConditionalValue) {
			if (!((ConditionalValue)value).set)
				return this;
			
			value = ((ConditionalValue)value).value;
		}
		
		SchemaResource schema = ResourceHub.getResources().getSchema();
		dcraft.schema.DbField fld = schema.getDbField(this.table, name);
		
		if ((fld == null) || (!fld.isDynamic() && !fld.isList())) 
			return this;
		
		FieldRequest dfld = new FieldRequest()
			.withName(name)
			.withValue(value)
			.withSubKey(subkey)
			.withUpdateOnly();
		
		this.withFields(dfld);
		
		if (fld.isDynamic())
			dfld.withFrom(this.when);

		return this;
	}

	public DbRecordRequest withSetField(String name, String subkey, Object value, BigDateTime from) {
		if (value instanceof ConditionalValue) {
			if (!((ConditionalValue)value).set)
				return this;
			
			value = ((ConditionalValue)value).value;
		}
		
		SchemaResource schema = ResourceHub.getResources().getSchema();
		dcraft.schema.DbField fld = schema.getDbField(this.table, name);
		
		if ((fld == null) || !fld.isDynamic()) 
			return this;
		
		FieldRequest dfld = new FieldRequest()
			.withName(name)
			.withValue(value)
			.withSubKey(subkey)
			.withFrom(from);
		
		this.withFields(dfld);
		
		return this;
	}

	public DbRecordRequest withSetField(String name, String subkey, Object value, BigDateTime from, BigDateTime to) {
		if (value instanceof ConditionalValue) {
			if (!((ConditionalValue)value).set)
				return this;
			
			value = ((ConditionalValue)value).value;
		}
		
		SchemaResource schema = ResourceHub.getResources().getSchema();
		dcraft.schema.DbField fld = schema.getDbField(this.table, name);
		
		if ((fld == null) || !fld.isDynamic() || !fld.isList()) 
			return this;
		
		FieldRequest dfld = new FieldRequest()
			.withName(name)
			.withValue(value)
			.withSubKey(subkey)
			.withFrom(from)
			.withTo(to);
		
		this.withFields(dfld);
		
		return this;
	}
	
	// where pairs = even are source and odd are dest
	public DbRecordRequest withConditionallySetFields(RecordStruct source, String... pairs) {
		for (int i = 0; i < (pairs.length - 1); i += 2) {
			if (source.hasField(pairs[i])) 
				this.withSetField(pairs[i+1], source.getField(pairs[i]));
		}
		
		return this;
	}
	
	public DbRecordRequest withConditionallyUpdateFields(RecordStruct source, String... pairs) {
		for (int i = 0; i < (pairs.length - 1); i += 2) {
			if (source.hasField(pairs[i])) 
				this.withUpdateField(pairs[i+1], source.getField(pairs[i]));
		}
		
		return this;
	}
	
	public DbRecordRequest withConditionallyUpdateTrFields(RecordStruct source, String locale, String... pairs) throws OperatingContextException {
		for (int i = 0; i < (pairs.length - 1); i += 2) {
			if (source.hasField(pairs[i]))
				this.withUpdateTrField(locale, pairs[i+1], source.getField(pairs[i]));
		}
		
		return this;
	}
	
	public DbRecordRequest withSetList(String name, ListStruct values) {
		this.sets.withItem(new RecordStruct()
			.with("Field", name)
			.with("Values", values)
		);
		
		return this;
	}
	
	public DbRecordRequest withConditionallySetList(RecordStruct source, String sname, String dname) {
		if (!source.hasField(sname))
			return this;
		
		this.sets.withItem(new RecordStruct()
			.with("Field", dname)
			.with("Values", source.getFieldAsList(sname))
		);
		
		return this;
	}
	
	public DbRecordRequest withRetireField(String name) {
		SchemaResource schema = ResourceHub.getResources().getSchema();
		dcraft.schema.DbField fld = schema.getDbField(this.table, name);
		
		if (fld == null) 
			return this;
		
		FieldRequest dfld = new FieldRequest()
			.withName(name)
			.withRetired();
		
		this.withFields(dfld);
		
		return this;
	}

	public DbRecordRequest withRetireField(String name, String subkey) {
		SchemaResource schema = ResourceHub.getResources().getSchema();
		dcraft.schema.DbField fld = schema.getDbField(this.table, name);
		
		if (fld == null) 
			return this;
		
		FieldRequest dfld = new FieldRequest()
			.withName(name)
			.withRetired()
			.withSubKey(subkey);
		
		this.withFields(dfld);
		
		return this;
	}
	
	@Override
	public CompositeStruct buildParams() {
		RecordStruct flds = new RecordStruct();
		
		for (FieldRequest fld : this.fields)
			flds.with(fld.getName(), fld.getParams(flds));

		this.parameters
			.with("Table", this.table)
			.with("Fields", flds)
			.with("When", this.when);
		
		if (StringUtil.isNotEmpty(this.id))
			this.parameters.with("Id", this.id);
		
		if (this.sets.size() > 0)
			this.parameters.with("Sets", this.sets);
		
		return super.buildParams();
	}
}
