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

import dcraft.hub.op.OperatingContextException;
import dcraft.script.inst.*;
import dcraft.script.inst.file.Stream;
import dcraft.script.work.ExecuteState;
import dcraft.script.work.InstructionWork;
import dcraft.script.work.ReturnOption;
import dcraft.xml.XElement;
import dcraft.xml.XNode;

import java.util.List;

public class ScriptServiceSwitch extends BlockInstruction {
	static public ScriptServiceSwitch tag() {
		ScriptServiceSwitch el = new ScriptServiceSwitch();
		el.setName("dcs.ScriptServiceSwitch");
		return el;
	}
	
	@Override
	public XElement newNode() {
		return ScriptServiceSwitch.tag();
	}
	
	@Override
	public ReturnOption run(InstructionWork state) throws OperatingContextException {
		if (state.getState() == ExecuteState.READY) {
			List<XNode> children = this.children;

			this.clearChildren();

			this.with(
					Var.tag()
						.attr("Name", "Response")
						.attr("Type", "Record")
						.with(
								XElement.tag("SetField")
									.attr("Name", "Code")
									.attr("Value", "1")
						),
					If.tag()
						.attr("Target", "$_Controller.Request.PostData")
						.attr("IsEmpty", "false")
						.with(
							Var.tag()
									.attr("Name", "Request")
									.attr("Type", "Record")
									.with(
											XElement.tag("Set")
												.withCData("{$_Controller.Request.PostData}")
									),
							Switch.tag()
								.attr("Target", "$Request.Op")
								.withAll(children)
						),
					If.tag()
						.attr("Target", "$Response.Code")
						.attr("Equal", "2")
						.with(
								With.tag()
										.attr("Target", "$Response")
										.with(
												XElement.tag("SetField")
														.attr("Name", "Message")
														.attr("Value", "Operation not found")
										)
						),
					With.tag()
						.attr("Target", "$_Controller.Response.Headers")
						.with(
								XElement.tag("SetField")
										.attr("Name", "Content-Type")
										.attr("Value", "application/json")
						),
					Stream.tag()
						.attr("Source", "$Response")
						.attr("Destination", "$Dest")
			);

			if (this.gotoTop(state))
				return ReturnOption.CONTINUE;
		}
		else {
			if (this.gotoNext(state, false))
				return ReturnOption.CONTINUE;
		}
		
		return ReturnOption.DONE;
	}
}
