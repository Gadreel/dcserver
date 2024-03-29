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

/**
 * Update a record in dcDatabase, see dcUpdateRecord schema.
 * Limit to 1MB of values (total size)
 * 
 * @author Andy
 *
 */
public class UpdateRecordRequest extends DbRecordRequest {
	static public UpdateRecordRequest update() {
		return new UpdateRecordRequest();
	}

	public UpdateRecordRequest() {
		super("dcUpdateRecord"); 
	}
}
