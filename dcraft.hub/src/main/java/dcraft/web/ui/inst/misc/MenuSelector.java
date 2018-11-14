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
package dcraft.web.ui.inst.misc;

import dcraft.hub.ResourceHub;
import dcraft.hub.op.OperatingContextException;
import dcraft.script.StackUtil;
import dcraft.script.inst.Instruction;
import dcraft.script.work.InstructionWork;
import dcraft.script.work.OperationsWork;
import dcraft.script.work.ReturnOption;
import dcraft.struct.RecordStruct;
import dcraft.struct.Struct;
import dcraft.util.StringUtil;
import dcraft.web.ui.inst.MenuWidget;
import dcraft.xml.XElement;

import java.util.List;

public class MenuSelector extends Instruction {
	static public MenuSelector tag() {
		MenuSelector el = new MenuSelector();
		el.setName("dc.MenuSelector");
		return el;
	}
	
	@Override
	public XElement newNode() {
		return MenuSelector.tag();
	}
	
	@Override
	public ReturnOption run(InstructionWork state) throws OperatingContextException {
		XElement menu = Struct.objectToXml(StackUtil.refFromSource(state, "Source"));
		long depth = StackUtil.intFromSource(state, "Level", 1);

		MenuWidget.LevelInfo menulevel = MenuWidget.findLevel(state, menu, (int) depth, 1);

		if (menulevel != null) {
			RecordStruct var = RecordStruct.record()
					.with("Menu", menulevel.level)
					.with("Slug", menulevel.slug);

			String name = StackUtil.stringFromSource(state, "Result");

			StackUtil.addVariable(state, name, var);

			// invalid - alternative? ((OperationsWork) state).setTarget(var);
		}

		return ReturnOption.CONTINUE;
	}
}
