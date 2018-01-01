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
 * Insert a new record into dcDatabase, see dcInsertRecord schema.
 * 
 * @author Andy
 *
 */
public class InsertRecordRequest extends DbRecordRequest {
	static public InsertRecordRequest insert() {
		return new InsertRecordRequest();
	}

	public InsertRecordRequest() {
		super("dcInsertRecord");
	}
}
