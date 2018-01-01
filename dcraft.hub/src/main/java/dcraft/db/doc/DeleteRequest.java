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

public class DeleteRequest extends DataRequest {
	public DeleteRequest(String id) {
		this(id, null);
	}
	
	public DeleteRequest(String id, String path) {
		super("dcDeleteDocument");
		
		/* TODO
		this.parameters.setField("Id", id);		
		
		if (StringUtil.isNotEmpty(path))
			this.parameters.setField("Path", path);
			*/
	}
}
