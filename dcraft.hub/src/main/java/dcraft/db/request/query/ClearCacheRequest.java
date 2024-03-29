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
package dcraft.db.request.query;

import dcraft.db.request.DataRequest;
import dcraft.struct.RecordStruct;

/**
 * Clears the cache for a given cache id.
 * 
 * @author Andy
 *
 */
public class ClearCacheRequest extends DataRequest {
	/**
	 * @param id of query cache
	 */
	public ClearCacheRequest(String id) {
		super("dcClearCache");
		
		this.parameters
			.with("CacheId", id);
	}
}
