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
import dcraft.log.Logger;
import dcraft.log.count.CountHub;
import dcraft.mail.CommInfo;
import dcraft.mail.MailUtil;
import dcraft.script.Script;
import dcraft.struct.RecordStruct;
import dcraft.struct.Struct;
import dcraft.struct.scalar.StringStruct;
import dcraft.task.IWork;
import dcraft.task.TaskContext;
import dcraft.util.IOUtil;
import dcraft.util.StringUtil;
import dcraft.xml.XElement;

public class DCScriptAdapter extends SimpleAdapter {
	@Override
	public void init(CommInfo info) throws OperatingContextException {
		super.init(info);

		this.file = ResourceHub.getResources().getComm().findCommFile(info.folder.resolve("code-email.dcs.xml"));
	}

	/*
	@Override
	protected void init(TaskContext taskctx) throws OperatingContextException {
		super.init(taskctx);

		if (this.script == null) {
			Logger.error("Missing or bad email script: " + this.commInfo.folder);
			taskctx.returnEmpty();
			return;
		}

		RecordStruct proc = Struct.objectToRecord(taskctx.queryVariable("_Process"));

		if (proc == null) {
			Logger.error("Missing comm process for email script: " + this.commInfo.folder);
			taskctx.returnEmpty();
			return;
		}

		RecordStruct resp = proc.getFieldAsRecord("Response");

		if (resp == null) {
			Logger.error("Missing comm response for email script: " + this.commInfo.folder);
			taskctx.returnEmpty();
			return;
		}

		this.then(this.script);

		this.then(new IWork() {
			@Override
			public void run(TaskContext taskctx) throws OperatingContextException {
				//resp.with("Text", resp.getFieldAsXml("Text"));
				//resp.with("Html", resp.getFieldAsXml("Html"));

				taskctx.returnValue(resp);
			}
		});
	}

	 */

	@Override
	protected void textToScript(RecordStruct proc, RecordStruct resp, CharSequence text) throws OperatingContextException {
		Script script = Script.of(text);

		this.then(script);
	}
}
