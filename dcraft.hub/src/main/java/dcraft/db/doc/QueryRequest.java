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
package dcraft.db.doc;

import dcraft.db.request.DataRequest;
import dcraft.struct.RecordStruct;
import dcraft.util.StringUtil;

public class QueryRequest extends DataRequest {
	public QueryRequest(String id) {
		this(id, null);
	}
	
	public QueryRequest(String id, String path) {
		super("dcQueryDocument");

		this.parameters.with("Id", id);
		
		if (StringUtil.isNotEmpty(path))
			this.parameters.with("Path", path);
	}
}
