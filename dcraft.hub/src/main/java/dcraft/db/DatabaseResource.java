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
package dcraft.db;

import dcraft.hub.op.OperationMarker;
import dcraft.hub.resource.ResourceBase;
import dcraft.hub.resource.ResourceTier;
import dcraft.log.Logger;
import dcraft.struct.Struct;
import dcraft.util.MimeInfo;
import dcraft.util.StringUtil;
import dcraft.xml.XElement;

import java.util.HashMap;

/**
 */
public class DatabaseResource extends ResourceBase {
	// types with ids
	protected HashMap<String, IConnectionManager> dcdatabases = new HashMap<>();

	public DatabaseResource() {
		this.setName("Database");
	}
	
	public DatabaseResource getParentResource() {
		if (this.tier == null)
			return null;
		
		ResourceTier pt = this.tier.getParent();
		
		if (pt != null)
			return pt.getDatabases();
		
		return null;
	}
	
	/*
	 * TODO make it so we can resuse connection managers when a tier retarts
	 * TODO actually just reuse the DatabaseResource in the new Tier instance...
	 * if need to switch the db connection settings for real, server will need a restart
	 */
	public void load(XElement dbinfo) {
		String dbname = dbinfo.getAttribute("Name", "default");
		String cname = dbinfo.getAttribute("Class", "dcraft.db.rocks.ConnectionManager");
		
		if (Logger.isDebug())
			Logger.debug("Loading database: " + dbname);
		
		try (OperationMarker om = OperationMarker.create()) {
			Class<?> dbclass = Class.forName(cname);
			IConnectionManager db = (IConnectionManager) dbclass.newInstance();
			
			db.load(dbinfo);
			
			if (! om.hasErrors()) {
				this.dcdatabases.put(dbname, db);
				
				if (Logger.isDebug())
					Logger.debug("Loaded database: " + dbname);
			}
			else {
				Logger.error("Unable to load/start database class: " + cname + " for: " + dbname);
			}
		}
		catch (Exception x) {
			Logger.error("Unable to load/start database class: " + x);
		}
	}
	
	public boolean hasDefaultDatabase() {
		IConnectionManager mt = this.dcdatabases.get("default");
		
		if (mt != null)
			return true;
		
		DatabaseResource parent = this.getParentResource();
		
		if (parent != null)
			return parent.hasDefaultDatabase();
		
		return false;
	}
	
	public IConnectionManager getDatabase() {
		return this.getDatabase("default");
	}
	
	public IConnectionManager getDatabase(String name) {
		if (StringUtil.isEmpty(name))
			return null;
		
		IConnectionManager mt = this.dcdatabases.get(name);
		
		if (mt != null)
			return mt;
		
		DatabaseResource parent = this.getParentResource();
		
		if (parent != null)
			return parent.getDatabase(name);
		
		return null;
	}
	
	@Override
	public void cleanup() {
		for (IConnectionManager conn : this.dcdatabases.values())
			conn.stop();
		
		this.dcdatabases.clear();
	}
}
