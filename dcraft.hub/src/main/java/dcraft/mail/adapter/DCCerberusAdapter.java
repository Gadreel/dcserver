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
import dcraft.log.Logger;
import dcraft.log.count.CountHub;
import dcraft.mail.CommInfo;
import dcraft.mail.MailUtil;
import dcraft.mail.dcc.HtmlPrinter;
import dcraft.script.Script;
import dcraft.script.StackUtil;
import dcraft.struct.ListStruct;
import dcraft.struct.RecordStruct;
import dcraft.struct.Struct;
import dcraft.task.IWork;
import dcraft.task.TaskContext;
import dcraft.util.StringUtil;
import dcraft.xml.XElement;
import dcraft.xml.XmlPrinter;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Path;

public class DCCerberusAdapter extends BaseAdapter {
	protected Script script = null;

	@Override
	public void init(CommInfo info) throws OperatingContextException {
		super.init(info);

		this.script = ResourceHub.getResources().getComm().loadScript(info.folder.resolve("code-email.dcc.xml"));
	}

	@Override
	protected void init(TaskContext taskctx) throws OperatingContextException {
		super.init(taskctx);

		if (this.script == null) {
			Logger.error("Missing or bad dcc script: " + this.commInfo.folder);
			taskctx.returnEmpty();
			return;
		}

		RecordStruct proc = Struct.objectToRecord(taskctx.queryVariable("_Process"));

		if (proc == null) {
			Logger.error("Missing comm process for dcc script: " + this.commInfo.folder);
			taskctx.returnEmpty();
			return;
		}

		RecordStruct resp = proc.getFieldAsRecord("Response");

		if (resp == null) {
			Logger.error("Missing comm response for dcc script: " + this.commInfo.folder);
			taskctx.returnEmpty();
			return;
		}

		this
			.then(script.toWork())
			.then(new IWork() {
				@Override
				public void run(TaskContext taskctx) throws OperatingContextException {
					System.out.println("req 2: " + resp);

					XElement doc = DCCerberusAdapter.this.script.getXml();

					XmlPrinter prt = new HtmlPrinter();

					try (ByteArrayOutputStream os = new ByteArrayOutputStream(); PrintStream ps = new PrintStream(os)) {
						prt.setFormatted(true);
						prt.setOut(ps);
						prt.print(doc);

						resp.with("Html", os.toString());
					}
					catch (OperatingContextException x) {
						Logger.warn("output restricted: " + x);
					}
					catch (IOException x) {
						Logger.warn("output failed: " + x);
					}

					taskctx.returnValue(resp);
				}
			});
	}
}
