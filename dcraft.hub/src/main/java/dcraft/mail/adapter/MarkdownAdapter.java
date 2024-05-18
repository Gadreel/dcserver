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
import dcraft.hub.op.OperationContext;
import dcraft.log.count.CountHub;
import dcraft.mail.CommInfo;
import dcraft.mail.MailUtil;
import dcraft.script.Script;
import dcraft.struct.scalar.StringStruct;
import dcraft.task.IWork;
import dcraft.task.TaskContext;

import java.nio.file.Path;

public class MarkdownAdapter extends BaseAdapter {
	protected Path file = null;

	@Override
	public void init(CommInfo info) throws OperatingContextException {
		super.init(info);

		// TODO search locales

		this.file = ResourceHub.getResources().getComm().findCommFile(info.folder.resolve("code-email.md"));
	}

	@Override
	protected void init(TaskContext taskctx) throws OperatingContextException {
		super.init(taskctx);

		MailUtil.MDParseResult mdParseResult = MailUtil.mdToDynamic(this, this.file);

		if (mdParseResult != null) {
			taskctx.addVariable("MetaFields", mdParseResult.fields);

			//this.setTextResult(StringStruct.of(mdParseResult.markdown));

			this.then(mdParseResult.script.toWork());

			// TODO output work
		}
	}
}
