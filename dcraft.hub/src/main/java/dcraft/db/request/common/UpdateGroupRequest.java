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
import dcraft.db.request.update.UpdateRecordRequest;
import dcraft.struct.CompositeStruct;
import dcraft.struct.ListStruct;
import dcraft.struct.RecordStruct;

/**
 * Update a group record.  Name is required.
 * 
 * @author Andy
 *
 */
public class UpdateGroupRequest extends UpdateRecordRequest {
	protected ConditionalValue name = new ConditionalValue();
	protected ConditionalValue desc = new ConditionalValue();
	protected ListStruct badges = null;
			
	public void setName(String v) {
		this.name.setValue(v);
	}
	
	public void setBadges(ListStruct tags) {
		this.badges = tags;
	}
	
	public void emptyBadges(ListStruct tags) {
		this.badges = new ListStruct();
	}
	
	public void addBadges(String... tags) {
		if (this.badges == null)
			this.badges = new ListStruct();
		
		for (String name : tags)
			this.badges.withItem(name);
	}
	
	public void setDescription(String v) {
		this.desc.setValue(v);
	}
	
	public UpdateGroupRequest(String id) {
		this.withTable("dcGroup");
		this.withId(id);
	}
	
	@Override
	public RecordStruct buildParams() {
		this.withSetField("dcName", this.name);
		this.withSetField("dcDescription", this.desc);

		// warning - setting an empty list removes all tags
		if (this.badges != null)
			this.withSetList("dcBadges", this.badges);
		
		return super.buildParams();
	}
}
