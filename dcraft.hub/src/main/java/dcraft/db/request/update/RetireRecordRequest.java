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

import dcraft.db.request.DataRequest;
import dcraft.struct.RecordStruct;

/**
 * dcDatabase does not remove records, but it does have the concept of retiring a record.
 * A retired record will not show up in a standard DivConq query, though of course
 * you can code a query to use it because the data is all there.
 * 
 * @author Andy
 *
 */
public class RetireRecordRequest extends DataRequest {
	/**
	 * @param table name
	 * @param id of record
	 */
	static public RetireRecordRequest of(String table, String id) {
		RetireRecordRequest request = new RetireRecordRequest();

		request.parameters
			.with("Table", table)
			.with("Id", id);

		return request;
	}

	public RetireRecordRequest() {
		super("dcRetireRecord");
	}
}
