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
package dcraft.web.adapter;

import dcraft.hub.op.OperatingContextException;
import dcraft.script.Script;
import dcraft.web.ui.UIUtil;

public class MarkdownIndexAdapter extends DynamicIndexAdapter {
	@Override
	public Script getSource() throws OperatingContextException {
		if (this.script != null)
			return this.script;

		this.script = UIUtil.mdToDynamic(this.file);
		
		return this.script;
	}
}
