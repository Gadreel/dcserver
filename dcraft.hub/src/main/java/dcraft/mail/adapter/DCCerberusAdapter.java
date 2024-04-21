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

import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
import dcraft.log.count.CountHub;
import dcraft.mail.MailUtil;
import dcraft.script.Script;
import dcraft.struct.ListStruct;
import dcraft.struct.RecordStruct;
import dcraft.task.IWork;
import dcraft.task.TaskContext;

public class DCCerberusAdapter extends BaseAdapter {
	@Override
	protected void init(TaskContext taskctx) throws OperatingContextException {
		super.init(taskctx);

		CountHub.countObjects("dcEmailScriptCount-" + OperationContext.getOrThrow().getTenant().getAlias(), this);

		Script script = Script.of(DCCerberusAdapter.this.file);

		RecordStruct page = RecordStruct.record()
				.with("Path", this.emailpath)
				.with("View", this.view);

		taskctx.addVariable("_Email", page);

		if (script != null)
			this.then(script.toWork())
				.then(new IWork() {
					@Override
					public void run(TaskContext taskctx) throws OperatingContextException {
						//		.then(DynamicOutputWriter.of(script));

						// TODO output work

						//DCCerberusAdapter.this.setHtmlResult(content);

						taskctx.returnResult();
					}
				});
	}
}
