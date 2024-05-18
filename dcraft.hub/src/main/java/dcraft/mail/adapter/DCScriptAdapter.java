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
package dcraft.mail.adapter;

import dcraft.filestore.CommonPath;
import dcraft.hub.ResourceHub;
import dcraft.hub.op.OperatingContextException;
import dcraft.log.count.CountHub;
import dcraft.mail.CommInfo;
import dcraft.mail.MailUtil;
import dcraft.script.Script;
import dcraft.struct.scalar.StringStruct;
import dcraft.task.IWork;
import dcraft.task.TaskContext;

public class DCScriptAdapter extends BaseAdapter {
	protected Script script = null;

	@Override
	public void init(CommInfo info) throws OperatingContextException {
		super.init(info);

		this.script = ResourceHub.getResources().getComm().loadScript(info.folder.resolve("code-email.dcs.xml"));
	}

	@Override
	protected void init(TaskContext taskctx) throws OperatingContextException {
		super.init(taskctx);

		if (script != null)
			this.then(script.toWork());
	}
}
