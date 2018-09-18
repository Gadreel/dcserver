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
package dcraft.script.inst.ext;

import dcraft.hub.app.ApplicationHub;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
import dcraft.mail.SmtpWork;
import dcraft.script.StackUtil;
import dcraft.script.inst.Instruction;
import dcraft.script.inst.doc.Base;
import dcraft.script.work.ExecuteState;
import dcraft.script.work.InstructionWork;
import dcraft.script.work.ReturnOption;
import dcraft.struct.ListStruct;
import dcraft.struct.RecordStruct;
import dcraft.struct.Struct;
import dcraft.struct.scalar.AnyStruct;
import dcraft.struct.scalar.BinaryStruct;
import dcraft.util.StringUtil;
import dcraft.xml.XElement;

public class SendEmail extends Instruction {
	static public SendEmail tag() {
		SendEmail el = new SendEmail();
		el.setName("dcs.SendEmail");
		return el;
	}

	@Override
	public XElement newNode() {
		return SendEmail.tag();
	}

	@Override
	public ReturnOption run(InstructionWork stack) throws OperatingContextException {
		if (stack.getState() == ExecuteState.READY) {
			String from = StackUtil.resolveValueToString(stack,  this.attr("From"), true);        // optional
			String reply = StackUtil.resolveValueToString(stack,  this.attr("ReplyTo"), true);        // optional
			String to = StackUtil.resolveValueToString(stack, this.attr("To"), true);

			if (StringUtil.isEmpty(to)) {
				String tolist = StackUtil.stringFromSource(stack, "ToList", "WebMaster");

				if (StringUtil.isNotEmpty(tolist)) {
					XElement catalog = ApplicationHub.getCatalogSettings("Email-List-" + tolist, null);

					if (catalog != null)
						to = catalog.getAttribute("To");
				}
			}

			// direct send
			String subject = StackUtil.stringFromSource(stack, "Subject");
			
			Struct textdoc = StackUtil.refFromSource(stack,"TextMessage");
			Struct htmldoc = StackUtil.refFromSource(stack,"HtmlMessage");
			
			String text = null;
			String html = null;
			
			if (textdoc instanceof Base)
				text = ((XElement) textdoc).getText();
			else if ((textdoc instanceof AnyStruct) && (((AnyStruct) textdoc).getValue() instanceof Base))
				text = ((XElement) ((AnyStruct) textdoc).getValue()).getText();
			else
				text = this.hasText()
						? StackUtil.resolveValueToString(stack, this.getText(), true)
						: StackUtil.resolveValueToString(stack, this.getAttribute("Body", "[under construction]"), true);
			
			if (htmldoc instanceof Base)
				html = ((XElement) htmldoc).toPrettyString();
			else if ((htmldoc instanceof AnyStruct) && (((AnyStruct) htmldoc).getValue() instanceof Base))
				html = ((XElement) ((AnyStruct) htmldoc).getValue()).toPrettyString();

			if (text != null)
				text = StackUtil.resolveValueToString(stack, text,true);

			if (html != null)
				html = StackUtil.resolveValueToString(stack, html,true);

			// probably not, but maybe in some cases?
			// body = UIUtil.regularTextToMarkdown(body);

			RecordStruct args = RecordStruct.record()
					.with("To", to)
					.with("From", from)
					.with("ReplyTo", reply)
					.with("Subject", subject)
					.with("Html", html)
					.with("Text", text);
			
			ListStruct attachments = ListStruct.list();
			
			for (XElement op : this.selectAll("*")) {
				if ("Attachment".equals(op.getName())) {
					Struct attach = StackUtil.refFromElement(stack, op, "Target");
					
					if (attach instanceof BinaryStruct)
						attachments.with(RecordStruct.record()
								.with("Name", StackUtil.stringFromElement(stack, op, "Name"))
								.with("Mime", StackUtil.stringFromElement(stack, op, "Mime"))
								.with("Content", attach)
						);
					else if (attach != null)
						attachments.with(RecordStruct.record()
								.with("Name", StackUtil.stringFromElement(stack, op, "Name"))
								.with("Mime", StackUtil.stringFromElement(stack, op, "Mime"))
								.with("File", attach)
						);
				}
			}
			
			if (attachments.size() > 0)
				args.with("Attachments", attachments);
			
			stack.setState(ExecuteState.RESUME);

			OperationContext.getAsTaskOrThrow().resumeWith(new SmtpWork(args));

			return ReturnOption.AWAIT;
		}

		return ReturnOption.CONTINUE;
	}
}
