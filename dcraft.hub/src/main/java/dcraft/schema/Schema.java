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

import dcraft.xml.XElement;

public class Schema {
	static public Schema create(String pathname, SchemaResource manager) {
		Schema sch = new Schema();
		sch.manager = manager;
		sch.file = pathname;
		return sch;
	}
	
	protected SchemaResource manager = null;
	protected String file = null;
	
	// used with includes as well
	public void loadSchema(XElement def) {
		if (def == null)
			return;
		
		XElement shared = def.find("Shared");
		
		if (shared != null) {
			for (XElement dtel : shared.selectAll("*")) {
				this.manager.loadDataType(this, dtel);
			}			
		}
		
		XElement db = def.find("Database");
		
		if (db != null) 
			this.manager.loadDb(this, db);
		
		
		XElement ser = def.find("Services");
		
		if (ser != null) 
			this.manager.loadService(this, ser);
	}

}
