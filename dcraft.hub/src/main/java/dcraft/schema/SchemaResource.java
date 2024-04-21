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

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import dcraft.filestore.CommonPath;
import dcraft.hub.resource.ResourceBase;
import dcraft.hub.resource.ResourceTier;
import dcraft.log.Logger;
import dcraft.schema.DataType.DataKind;
import dcraft.schema.ServiceSchema.Op;
import dcraft.struct.ListStruct;
import dcraft.struct.RecordStruct;
import dcraft.util.ArrayUtil;
import dcraft.util.StringUtil;
import dcraft.xml.XElement;
import dcraft.xml.XmlReader;

/**
 * DivConq uses a specialized type system that provides type consistency across services 
 * (including web services), database fields and stored procedures, as well as scripting.
 * 
 * All scalars (including primitives) and composites (collections) are wrapped by some
 * subclass of Struct.  List/array collections are expressed by this class.  
 * This class is analogous to an Array in JSON but may contain type information as well, 
 * similar to Yaml.
 * 
 * There are schema files (written in Xml and stored in the Packages repository) that define
 * all the known data types, including complex data types.  These schema files get compiled
 * for for a given project and deployed as part of the conf directory.
 * 
 * This class oversees the management of all the known data types as well as database
 * tables, stored procedures and services (including web services).
 *
 *
 * NOTE: there are effectively two levels of schema, top level and tenant level. Sites and other tiers should not
 * have schema because for schema to fully function it should be reloaded and recompiled at each level (to get overrides).
 * However, it is possible to add to schema at user or site level and it will mostly work, just not all the overrides. It
 * is not a best practice, but could work.
 *
 */
public class SchemaResource extends ResourceBase {
	static public SchemaResource fromFile(Path fl) {
		SchemaResource grp = new SchemaResource();
		grp.loadSchema(fl);
		grp.compile();
		return grp;
	}

	static public SchemaResource fromXml(XElement schema) {
		return SchemaResource.fromXml("Default", schema);
	}

	static public SchemaResource fromXml(String filename, XElement schema) {
		SchemaResource grp = new SchemaResource();
		grp.loadSchema(filename, schema);
		grp.compile();
		return grp;
	}
	
	// composite schema of database
	protected DatabaseSchema db = new DatabaseSchema(this);
	
	// composite schema of database
	protected ServiceSchema service = new ServiceSchema(this);
	
	// types with ids
	protected HashMap<String, DataType> knownTypes = new HashMap<>();
	
	protected List<XElement> definitions = new ArrayList<>();

	protected boolean blockParent = false;

	public SchemaResource() {
		this.setName("Schema");
	}
	
	public SchemaResource getParentResource() {
		if ((this.tier == null) || this.blockParent)
			return null;
		
		ResourceTier pt = this.tier.getParent();
		
		if (pt != null)
			return pt.getSchema();
		
		return null;
	}

	public SchemaResource withBlockParent() {
		this.blockParent = true;
		return this;
	}

	public void addDefinition(XElement def) {
		this.definitions.add(def);
	}
	
	public Collection<XElement> getDefinitions() {
		return this.definitions;
	}
	
	public boolean hasTable(String table) {
		boolean fnd = this.db.hasTable(table);
		
		if (fnd)
			return true;
		
		SchemaResource parent = this.getParentResource();

		if (parent == null)
			return false;
		
		return parent.hasTable(table);
	}
	
	// collect a list of all the tables names available to this tier and parents
	public Set<String> getTableNames() {
		Set<String> tableNames = this.db.getTableNames();
		
		SchemaResource parent = this.getParentResource();
		
		if (parent != null)
			tableNames.addAll(parent.getTableNames());
		
		return tableNames;
	}
		
	public List<TableView> getTables() {
		Set<String> tableNames = this.getTableNames();
		
		List<TableView> views = new ArrayList<>();
		
		for (String name : tableNames)
			views.add(this.getTableView(name));
		
		return views;
	}
	
	/*
	public List<DbField> getDbFields(String table) {
		List<DbField> t = this.db.getFields(table);
		
		if (t == null)
			t = new ArrayList<>();
		
		SchemaResource parent = this.getParentResource();

		if (parent == null)
			return t;
		
		List<DbField> t2 = parent.getDbFields(table);
		
		if (t2 != null)
			t.addAll(t2);
		
		return t;
	}
	*/
	
	/*
	 * Goal here is to get a unified table view, loo[ all schema and build a composite view of the table
	 */
	public TableView getTableView(String table) {
		SchemaResource parent = this.getParentResource();

		DbTable def = this.db.getTable(table);
		
		if (parent != null) {
			TableView view = parent.getTableView(table);
			
			if (view == null) {
				if (def != null)
					return TableView.table(def);
			}
			else {
				if (def != null)
					view.mergeWith(def);	// overrides fields
				
				return view;
			}
		}
		else {
			if (def != null)
				return TableView.table(def);
		}
		
		return null;
	}
	
	public DbField getDbField(String table, String field) {
		DbField t = this.db.getField(table, field);
		
		if (t != null)
			return t;
		
		SchemaResource parent = this.getParentResource();

		if (parent == null)
			return null;
		
		return parent.getDbField(table, field);
	}
	
	public DbProc getDbProc(String name) {
		DbProc t = this.db.getProc(name);
		
		if (t != null)
			return t;
		
		SchemaResource parent = this.getParentResource();

		if (parent == null)
			return null;
		
		return parent.getDbProc(name);
	}
	
	public DbExpression getDbExpression(String name) {
		DbExpression t = this.db.getExpression(name);
		
		if (t != null)
			return t;
		
		SchemaResource parent = this.getParentResource();
		
		if (parent == null)
			return null;
		
		return parent.getDbExpression(name);
	}
	
	public DbComposer getDbComposer(String name) {
		DbComposer t = this.db.getComposer(name);
		
		if (t != null)
			return t;
		
		SchemaResource parent = this.getParentResource();

		if (parent == null)
			return null;
		
		return parent.getDbComposer(name);
	}
	
	public DbFilter getDbFilter(String name) {
		DbFilter t = this.db.getFilter(name);
		
		if (t != null)
			return t;
		
		SchemaResource parent = this.getParentResource();

		if (parent == null)
			return null;
		
		return parent.getDbFilter(name);
	}
	
	public DbCollector getDbCollector(String name) {
		DbCollector t = this.db.getCollector(name);
		
		if (t != null)
			return t;
		
		SchemaResource parent = this.getParentResource();

		if (parent == null)
			return null;
		
		return parent.getDbCollector(name);
	}
	
	// returns (copy) list of all triggers for all levels of the chain
	public List<DbTrigger> getDbTriggers(String table, String operation) {
		List<DbTrigger> t = this.db.getTriggers(table, operation);		
		
		if (t == null)
			t = new ArrayList<>();
		
		SchemaResource parent = this.getParentResource();

		if (parent == null)
			return t;
		
		List<DbTrigger> t2 = parent.getDbTriggers(table, operation);
		
		if (t2 != null)
			t.addAll(t2);
		
		return t;
	}
	
	// services just for this part of the chain
	public ServiceSchema getLocalServices() {
		return this.service;
	}

	public OpInfo getServiceOp(String service, String feature, String op) {
		Op ot = this.service.getOp(service, feature, op);
		String[] securityTags = this.service.getOpSecurity(service, feature, op);
		
		if (ot != null) {
			OpInfo oi = new OpInfo();
			oi.op = ot;
			oi.securityTags = securityTags;
			return oi;
		}
		
		SchemaResource parent = this.getParentResource();

		if (parent == null)
			return null;
		
		OpInfo oi = parent.getServiceOp(service, feature, op);
		
		if ((oi != null) && (securityTags != null)) 
			oi.securityTags = securityTags;
		
		return oi;
	}

	public class OpInfo {
		protected Op op = null;
		protected String[] securityTags = null;
		
		public Op getOp() {
			return this.op;
		}
		
		public String[] getSecurityTags() {
			if ((this.securityTags != null) && (this.op != null))
				return ArrayUtil.addAll(this.securityTags, this.op.securityTags);
			
			if (this.securityTags != null)
				return this.securityTags;
			
			if (this.op != null)
				return this.op.securityTags;
			
			return null;
		}
		
		public boolean isTagged(String... tags) {
			if (this.securityTags != null) { 
				for (int i = 0; i < this.securityTags.length; i++) {
					String has = this.securityTags[i];
	
					for (String wants : tags) {
						if (has.equals(wants))
							return true;
					}
				}
			}
			
			if (this.op != null)
				return this.op.isTagged(tags);
			
			return false;
		}
	}
	
	/**
	 * @param type schema name of type
	 * @return the schema data type
	 */
	public DataType getType(String type) { 
		DataType t = this.knownTypes.get(type);
		
		if (t != null)
			return t;
		
		SchemaResource parent = this.getParentResource();

		if (parent == null)
			return null;
		
		return parent.getType(type);
	}

	// ----------
	
	public void removeService(String name) {
		this.service.remove(name);
	}
	
	/**
	 * @return map of all known data types
	 */
	public Map<String, DataType> knownTypes() {
		return this.knownTypes;
	}
	
	/**
	 * Create a new record structure using a schema data type.
	 * 
	 * @param type type schema name of type
	 * @return initialized record structure
	 */
	public RecordStruct newRecord(String type) {
		DataType tp = this.getType(type);
		
		if ((tp == null) || (tp.kind != DataKind.Record))
			return null;
		
		return new RecordStruct(tp);
	}
	
	/**
	 * Schema files contain interdependencies, after loading the files call
	 * compile to resolve these interdependencies.
	 */
	public void compile() {
		// compiling not thread safe, do it once at start
		for (DataType dt : this.knownTypes.values()) 
			dt.compile();
		
		this.db.compile();
		
		this.service.compile();
	}

	/**
	 * Load a file containing schema into the master schema.
	 * 
	 * @param fl file to load 
	 * @return log of the load attempt
	 */
	public void loadSchema(Path fl) {
		if (fl == null) {
			Logger.errorTr(108, "Unable to apply schema file, file null");
			return;
		}
		
		if (Files.notExists(fl)) {
			Logger.errorTr(109, "Missing schema file, expected: " + fl);
			return;
		}
		
		XElement schema = XmlReader.loadFile(fl, false, true);
		
		if (schema == null) {
			Logger.errorTr(110, "Unable to apply schema file, missing xml: " + fl);
			return;
		}

		Schema s = Schema.create(fl.toString(), this);
		
		s.loadSchema(schema);
	}

	public void loadSchema(String name, XElement schema) {
		if (schema == null) {
			Logger.errorTr(110, "Unable to apply schema file, missing xml");
			return;
		}

		Schema s = Schema.create(name, this);
		
		s.loadSchema(schema);
	}

	/**
	 * Load type definition from an Xml element
	 *  
	 * @param schema to associate type with
	 * @param dtel xml source of the definition
	 * @return the schema data type
	 */
	public DataType loadDataType(Schema schema, XElement dtel) {
		DataType dtype = new DataType(schema);
		
		dtype.load(dtel);
		
		if (StringUtil.isNotEmpty(dtype.id))
			this.knownTypes.put(dtype.id, dtype);
		
		return dtype;
	}

	public List<DataType> lookupOptionsType(String name) {
		List<DataType> ld = new ArrayList<DataType>();
		
		if (name.contains(":")) {
			String[] parts = name.split(":");
			
			DataType t1 = this.getType(parts[0]);
			
			if (t1 == null) {
				Logger.error("Could not compile because type missing: " + name);
				return ld;
			}
			
			t1.compile();
			
			SchemaResource parent = this.getParentResource();
			
			if ((t1 == null) || (t1.fields == null)) {
				if (parent == null)
					return ld;
				
				return parent.lookupOptionsType(name);
			}
			
			Field f1 = t1.fields.get(parts[1]);
			
			if (f1 == null) {
				if (parent == null)
					return ld;
				
				return parent.lookupOptionsType(name);
			}
			
			return f1.options;
		}
		
		DataType d4 = this.getType(name);
		
		if (d4 != null)
			ld.add(d4);
		
		return ld;
	}

	public void loadDb(Schema schema, XElement xml) {
		this.db.load(schema, xml);
	}

	public void loadService(Schema schema, XElement xml) {
		this.service.load(schema, xml);
	}

	public OpInfo loadIsloatedInfo(CommonPath path, RecordStruct security, XElement opel) {
		Schema schema = Schema.create("dynamic: " + path, this);

		OpInfo info = new OpInfo();

		info.op = this.service.loadIsloated(schema, security, opel);

		return info;
	}

	public ListStruct toJsonDef(String... names) {
		ListStruct list = new ListStruct();
		
		for (String name : names) {
			DataType dt = this.getType(name);
			
			if (dt != null)
				list.withItem(dt.toJsonDef());
		}
		
		return list;
	}
}
