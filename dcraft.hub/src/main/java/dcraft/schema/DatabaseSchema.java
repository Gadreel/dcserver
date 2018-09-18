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

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import dcraft.util.StringUtil;
import dcraft.xml.XAttribute;
import dcraft.xml.XElement;

public class DatabaseSchema {
	protected SchemaResource man = null;
	protected HashMap<String, DbProc> procs = new HashMap<>();
	protected HashMap<String, DbExpression> expressions = new HashMap<>();
	protected HashMap<String, DbComposer> composers = new HashMap<>();
	protected HashMap<String, DbFilter> filters = new HashMap<>();
	protected HashMap<String, DbCollector> collectors = new HashMap<>();
	protected HashMap<String, List<DbTrigger>> triggers = new HashMap<>();
	protected HashMap<String, DbTable> tables = new HashMap<>();
	
	public Collection<DbProc> getProcedures() {
		return this.procs.values();
	}
	
	public Collection<DbExpression> getExpressions() {
		return this.expressions.values();
	}
	
	public Collection<DbComposer> getComposers() {
		return this.composers.values();
	}
	
	public Collection<DbFilter> getFilters() {
		return this.filters.values();
	}
	
	public Collection<DbCollector> getCollectors() {
		return this.collectors.values();
	}
	
	//public Collection<DbTable> getTables() {
	//	return this.tables.values();
	//}
	
	public DatabaseSchema(SchemaResource man) {
		this.man = man;
	}
	
	public void load(Schema schema, XElement db) {
		for (XElement dtel : db.selectAll("Table")) {
			String id = dtel.getAttribute("Id");
			
			if (StringUtil.isEmpty(id))
				continue;
			
			DbTable tab = this.tables.get(id);
			
			if (tab == null) {
				tab = new DbTable();
				tab.name = id;
				this.tables.put(id, tab);			
			}
			
			DataType dt = this.man.knownTypes().get(id);
			
			if (dt != null) 
				dt.load(dtel);
			else {
				dt = this.man.loadDataType(schema, dtel);
				
				XElement autoSchema = new XElement("Table",
						new XElement("Field",
								new XAttribute("Name", "Id"),
								new XAttribute("Type", "Id")
						),
						new XElement("Field",
								new XAttribute("Name", "Retired"),
								new XAttribute("Type", "Boolean")
						),
						new XElement("Field",
								new XAttribute("Name", "From"),
								new XAttribute("Type", "BigDateTime"),
								new XAttribute("Indexed", "True")
						),
						new XElement("Field",
								new XAttribute("Name", "To"),
								new XAttribute("Type", "BigDateTime"),
								new XAttribute("Indexed", "True")
						),
						new XElement("Field",
								new XAttribute("Name", "Tags"),
								new XAttribute("Type", "dcTinyString"),
								new XAttribute("List", "True")										
						)
				);

				
				// automatically add Id, Retired, etc to tables 
				dt.load(autoSchema);

				for (XElement fel : autoSchema.selectAll("Field")) 
					tab.addField(fel, dt);
			}

			for (XElement fel : dtel.selectAll("Field")) 
				tab.addField(fel, dt);
		}
		
		for (XElement procel : db.selectAll("Procedure")) {
			String sname = procel.getAttribute("Name");
			
			if (StringUtil.isEmpty(sname))
				continue;
			
			DbProc opt = new DbProc();
			opt.name = sname;
			opt.execute = procel.getAttribute("Execute");
			
			this.procs.put(sname, opt);
		}
		
		for (XElement procel : db.selectAll("Expression")) {
			String sname = procel.getAttribute("Name");
			
			if (StringUtil.isEmpty(sname))
				continue;
			
			DbExpression opt = new DbExpression();
			opt.name = sname;
			opt.execute = procel.getAttribute("Execute");
			
			this.expressions.put(sname, opt);
		}
		
		for (XElement procel : db.selectAll("Composer")) {
			String sname = procel.getAttribute("Name");
			
			if (StringUtil.isEmpty(sname))
				continue;
			
			DbComposer opt = new DbComposer();
			opt.name = sname;
			opt.execute = procel.getAttribute("Execute");
			opt.securityTags = procel.hasNotEmptyAttribute("Badges")
					? procel.getAttribute("Badges").split(",") : new String[] { "Guest", "User" };
			
			this.composers.put(sname, opt);
		}
		
		for (XElement procel : db.selectAll("Filter")) {
			String sname = procel.getAttribute("Name");
			
			if (StringUtil.isEmpty(sname))
				continue;
			
			DbFilter opt = new DbFilter();
			opt.name = sname;
			opt.execute = procel.getAttribute("Execute");
			//opt.securityTags = procel.hasNotEmptyAttribute("Badges")
			//		? procel.getAttribute("Badges").split(",") : new String[] { "Guest", "User" };
			
			this.filters.put(sname, opt);
		}
		
		for (XElement procel : db.selectAll("Collector")) {
			String sname = procel.getAttribute("Name");
			
			if (StringUtil.isEmpty(sname))
				continue;
			
			DbCollector opt = new DbCollector();
			opt.name = sname;
			opt.execute = procel.getAttribute("Execute");
			opt.securityTags = procel.hasNotEmptyAttribute("Badges")
					? procel.getAttribute("Badges").split(",") : new String[] { "Guest", "User" };
			
			this.collectors.put(sname, opt);
		}
		
		for (XElement procel : db.selectAll("Trigger")) {
			String sname = procel.getAttribute("Table");
			
			if (StringUtil.isEmpty(sname))
				continue;			
			
			DbTrigger opt = new DbTrigger();
			opt.op = procel.getAttribute("Operation");
			opt.execute = procel.getAttribute("Execute");
			opt.table = sname;
			
			List<DbTrigger> ll = this.triggers.get(sname);
			
			if (ll == null) {
				ll = new ArrayList<>();
				this.triggers.put(sname, ll);
			}
			
			ll.add(opt);
		}			
	}

	public void compile() {
		for (DbTable t : this.tables.values())
			t.compile(this.man);
	}
	
	public DbTable getTable(String table) {
		if (StringUtil.isEmpty(table))
			return null;
		
		return this.tables.get(table);
	}
	
	public DbField getField(String table, String field) {
		if (StringUtil.isEmpty(table) || StringUtil.isEmpty(field))
			return null;
		
		DbTable tbl = this.tables.get(table);
		
		if (tbl != null)		
			return tbl.fields.get(field);
		
		return null;
	}

	public boolean hasTable(String table) {
		if (StringUtil.isEmpty(table))
			return false;
		
		return this.tables.containsKey(table);
	}
	
	// returns a copy list, you (caller) can own the list 
	public List<DbTrigger> getTriggers(String table, String operation) {
		if (StringUtil.isEmpty(table) || StringUtil.isEmpty(operation))
			return null;
		
		List<DbTrigger> ret = new ArrayList<>();
		List<DbTrigger> tbl = this.triggers.get(table);
		
		if (tbl != null)
			for (DbTrigger t : tbl)
				if (t.op.equals(operation))
					ret.add(t);
		
		return ret;
	}
	
	// returns a copy list, you (caller) can own the list 
	public List<DbTable> getTables() {
		return new ArrayList<>(this.tables.values());
	}
	
	// returns a copy list, you (caller) can own the list
	public Set<String> getTableNames() {
		return new HashSet<>(this.tables.keySet());
	}
	
	// returns a copy list, you (caller) can own the list 
	public List<DbField> getFields(String table) {
		if (StringUtil.isEmpty(table))
			return null;
		
		DbTable tbl = this.tables.get(table);
		
		if (tbl == null)
			return null;
		
		return new ArrayList<>(tbl.fields.values());
	}
	
	public DbProc getProc(String name) {
		if (StringUtil.isEmpty(name))
			return null;
		
		return this.procs.get(name);
	}
	
	
	public DbExpression getExpression(String name) {
		if (StringUtil.isEmpty(name))
			return null;
		
		return this.expressions.get(name);
	}
	
	public DbComposer getComposer(String name) {
		if (StringUtil.isEmpty(name))
			return null;
		
		return this.composers.get(name);
	}
	
	public DbFilter getFilter(String name) {
		if (StringUtil.isEmpty(name))
			return null;
		
		return this.filters.get(name);
	}
	
	public DbCollector getCollector(String name) {
		if (StringUtil.isEmpty(name))
			return null;
		
		return this.collectors.get(name);
	}
}
