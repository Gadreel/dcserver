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
import dcraft.struct.RecordStruct;
import dcraft.struct.Struct;
import dcraft.struct.scalar.AnyStruct;
import dcraft.util.StringUtil;
import dcraft.xml.XElement;

public class CatalogSettings extends Instruction {
	static public CatalogSettings tag() {
		CatalogSettings el = new CatalogSettings();
		el.setName("dcs.CatalogSettings");
		return el;
	}

	@Override
	public XElement newNode() {
		return CatalogSettings.tag();
	}

	@Override
	public ReturnOption run(InstructionWork stack) throws OperatingContextException {
		if (stack.getState() == ExecuteState.READY) {
			String id = StackUtil.stringFromSource(stack, "Id");
			String alternate = StackUtil.stringFromSource(stack, "Alternate");

			XElement settings = ApplicationHub.getCatalogSettings(id, alternate);
			
			if (settings != null) {
				String result = StackUtil.stringFromSource(stack, "Result");
				
				if (StringUtil.isNotEmpty(result)) {
					StackUtil.addVariable(stack, result, settings);
				}
			}
			
			return ReturnOption.CONTINUE;
		}

		return ReturnOption.CONTINUE;
	}
}
