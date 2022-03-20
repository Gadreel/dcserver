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

import dcraft.db.IConnectionManager;
import dcraft.hub.op.OperationMarker;
import dcraft.hub.resource.ResourceBase;
import dcraft.hub.resource.ResourceTier;
import dcraft.log.Logger;
import dcraft.util.StringUtil;
import dcraft.xml.XElement;

import java.sql.SQLException;
import java.util.HashMap;

/**
 */
public class SqlDatabaseResource extends ResourceBase {
	// types with ids
	protected HashMap<String, SqlPool> sqlpoola = new HashMap<>();

	public SqlDatabaseResource() {
		this.setName("SqlDatabase");
	}
	
	public SqlDatabaseResource getParentResource() {
		if (this.tier == null)
			return null;
		
		ResourceTier pt = this.tier.getParent();
		
		if (pt != null)
			return pt.getSqlDatabases();
		
		return null;
	}
	
	/*
	 * TODO make it so we can resuse connection managers when a tier retarts
	 * TODO actually just reuse the DatabaseResource in the new Tier instance...
	 * if need to switch the db connection settings for real, server will need a restart
	 */
	public void load(XElement dbinfo) {
		String dbname = dbinfo.getAttribute("Name");
		String connection = dbinfo.getAttribute("Connection");
		
		if (Logger.isDebug())
			Logger.debug("Loading sql database: " + dbname);

		SqlPool db = SqlPool.of(dbname, connection);

		this.sqlpoola.put(dbname, db);
	}

	public SqlPool getSqlDatabase(String name) {
		if (StringUtil.isEmpty(name))
			return null;
		
		SqlPool mt = this.sqlpoola.get(name);
		
		if (mt != null)
			return mt;
		
		SqlDatabaseResource parent = this.getParentResource();
		
		if (parent != null)
			return parent.getSqlDatabase(name);
		
		return null;
	}

	public SqlConnection getSqlConnection(String name) throws SQLException {
		SqlPool pool = this.getSqlDatabase(name);

		if (pool != null)
			return pool.getSqlConnection();

		throw new SQLException("Database " + name + " does not exist.");
	}
	
	@Override
	public void cleanup() {
		for (SqlPool conn : this.sqlpoola.values())
			conn.stop();
		
		this.sqlpoola.clear();
	}
}
