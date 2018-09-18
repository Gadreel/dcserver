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
package dcraft.db.request.common;

import dcraft.db.request.update.ConditionalValue;
import dcraft.db.request.update.InsertRecordRequest;
import dcraft.struct.CompositeStruct;
import dcraft.struct.ListStruct;
import dcraft.struct.RecordStruct;

/**
 * Insert a new group record into dcDatabase.  Name is required.
 * 
 * @author Andy
 *
 */
public class AddGroupRequest extends InsertRecordRequest {
	protected ConditionalValue name = new ConditionalValue();
	protected ConditionalValue desc = new ConditionalValue();
	protected ListStruct badges = null;
			
	public void setName(String v) {
		this.name.setValue(v);
	}
	
	public void setDescription(String v) {
		this.desc.setValue(v);
	}
	
	public void setBadges(ListStruct v) {
		this.badges = v;
	}
	
	public void addBadges(String... v) {
		if (this.badges == null)
			this.badges = new ListStruct();
		
		for (String name : v)
			this.badges.withItem(name);
	}
	
	public AddGroupRequest(String name) {
		this.withTable("dcGroup");
		this.setName(name);
	}
	
	@Override
	public RecordStruct buildParams() {
		this.withSetField("dcName", this.name);
		this.withSetField("dcDescription", this.desc);
		this.withSetList("dcBadges", this.badges);
		
		return super.buildParams();
	}
}
