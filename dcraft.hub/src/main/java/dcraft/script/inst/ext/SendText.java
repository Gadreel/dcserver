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
import dcraft.hub.op.OperationOutcomeRecord;
import dcraft.interchange.twilio.SmsUtil;
import dcraft.log.Logger;
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
import dcraft.task.IWork;
import dcraft.task.TaskContext;
import dcraft.util.PeopleUtil;
import dcraft.util.StringUtil;
import dcraft.xml.XElement;

public class SendText extends Instruction {
	static public SendText tag() {
		SendText el = new SendText();
		el.setName("dcs.SendText");
		return el;
	}

	@Override
	public XElement newNode() {
		return SendText.tag();
	}

	@Override
	public ReturnOption run(InstructionWork stack) throws OperatingContextException {
		if (stack.getState() == ExecuteState.READY) {
			String to = StackUtil.stringFromSource(stack, "To");
			
			String cleanto = PeopleUtil.formatPhone(to);

			if (cleanto == null)
				return ReturnOption.CONTINUE;

			Struct textdoc = StackUtil.refFromSource(stack,"TextMessage");

			String text = null;

			if (textdoc instanceof Base)
				text = ((XElement) textdoc).getText();
			else if ((textdoc instanceof AnyStruct) && (((AnyStruct) textdoc).getValue() instanceof Base))
				text = ((XElement) ((AnyStruct) textdoc).getValue()).getText();
			else
				text = this.hasText() ? StackUtil.resolveValueToString(stack, this.getText()) : StackUtil.stringFromSource(stack, "Body", "[under construction]");

			if (text != null)
				text = StackUtil.resolveValueToString(stack, text,true);

			String ftext = text;

			stack.setState(ExecuteState.RESUME);

			OperationContext.getAsTaskOrThrow().resumeWith(new IWork() {
				@Override
				public void run(TaskContext taskctx) throws OperatingContextException {
					SmsUtil.sendText(null, cleanto, ftext, new OperationOutcomeRecord() {
						@Override
						public void callback(RecordStruct result) throws OperatingContextException {
							taskctx.returnEmpty();
						}
					});
				}
			});

			return ReturnOption.AWAIT;
		}

		return ReturnOption.CONTINUE;
	}
}
