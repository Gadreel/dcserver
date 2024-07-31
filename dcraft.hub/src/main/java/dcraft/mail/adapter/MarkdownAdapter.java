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
import dcraft.mail.dcc.UIUtil;
import dcraft.script.Script;
import dcraft.script.StackUtil;
import dcraft.struct.BaseStruct;
import dcraft.struct.RecordStruct;
import dcraft.struct.Struct;
import dcraft.struct.scalar.StringStruct;
import dcraft.task.IWork;
import dcraft.task.TaskContext;
import dcraft.util.IOUtil;
import dcraft.util.Memory;
import dcraft.util.StringUtil;
import dcraft.util.io.OutputWrapper;
import dcraft.web.md.MarkdownUtil;
import dcraft.xml.XElement;
import dcraft.xml.XNode;
import dcraft.xml.XmlPrinter;

import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.nio.file.Files;
import java.nio.file.Path;

public class MarkdownAdapter extends SimpleAdapter {
	@Override
	public void init(CommInfo info) throws OperatingContextException {
		super.init(info, ".md");
	}

	@Override
	protected void textToScript(RecordStruct proc, RecordStruct resp, CharSequence text) throws OperatingContextException {
		String code = "<dcs.Script><text Name=\"text\">\n" + text + "\n</text><dcs.Exit Result=\"$text\" /></dcs.Script>";

		Script script = Script.of(code);

		this.then(script);

		this.then(new IWork() {
			@Override
			public void run(TaskContext taskctx) throws OperatingContextException {
				resp.with("Text", UIUtil.formatText(proc, (XElement) taskctx.getResult()));

				if (resp.isFieldEmpty("Html")) {
					String output = resp.getFieldAsString("Text");

					if (StringUtil.isNotEmpty(output)) {
						XElement body = MarkdownUtil.process(output, true);

						resp.with("Html", body);
					}
				}

				taskctx.returnValue(resp);
			}
		});
	}
}
