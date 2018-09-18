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

import dcraft.db.proc.IComposer;
import dcraft.db.proc.IFilter;
import dcraft.hub.ResourceHub;
import dcraft.log.Logger;
import dcraft.util.StringUtil;

public class DbFilter {
	public String name = null;
	public String table = null;
	public String execute = null;
	public IFilter sp = null;
	
	public IFilter getFilter() {
		if (this.sp != null)
			return this.sp;
		
		// composer should be stateless, save effort by putting 1 instance inside DbComposer and reusing it
		if (StringUtil.isNotEmpty(this.execute))
			this.sp = (IFilter) ResourceHub.getResources().getClassLoader().getInstance(this.execute);
		
		if (this.sp == null)
			Logger.error("Filter " + this.name + " failed to create.");
		
		return this.sp;
	}
}