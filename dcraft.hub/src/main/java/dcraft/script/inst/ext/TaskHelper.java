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
import dcraft.hub.op.OperationContext;
import dcraft.log.Logger;
import dcraft.script.Script;
import dcraft.script.StackUtil;
import dcraft.script.inst.Instruction;
import dcraft.script.work.ExecuteState;
import dcraft.script.work.InstructionWork;
import dcraft.script.work.ReturnOption;
import dcraft.struct.BaseStruct;
import dcraft.struct.RecordStruct;
import dcraft.task.*;
import dcraft.util.StringUtil;
import dcraft.xml.XElement;

public class TaskHelper extends Instruction {
	static public TaskHelper tag() {
		TaskHelper el = new TaskHelper();
		el.setName("dcs.TaskHelper");
		return el;
	}

	@Override
	public XElement newNode() {
		return TaskHelper.tag();
	}

	@Override
	public ReturnOption run(InstructionWork stack) throws OperatingContextException {
		if (stack.getState() == ExecuteState.READY) {
			BaseStruct context = StackUtil.refFromSource(stack, "Context", true);
			BaseStruct params = StackUtil.refFromSource(stack, "Params", true);
			String title = StackUtil.stringFromSourceClean(stack, "Title");
			String topic = StackUtil.stringFromSourceClean(stack, "Topic", "Batch");
			String script = StackUtil.stringFromSourceClean(stack, "Script");
			String code = StackUtil.stringFromSourceClean(stack, "Code");

			Task task = (context != null) ? Task.of((RecordStruct) context) : Task.ofSubContext();

			if (StringUtil.isNotEmpty(title))
				task.withTitle(title);

			task.withTopic(topic);	// default to Batch

			if (StringUtil.isNotEmpty(script))
				task.withScript(script);
			else if (StringUtil.isNotEmpty(code))
				task.withWork(Script.of(code).toWork());

			if (params != null)
				task.withParams(params);

			// TODO support QueueHub as well

			String result = StackUtil.stringFromSourceClean(stack, "Result");

			if (StringUtil.isNotEmpty(result)) {
				TaskHub.submit(task, new TaskObserver() {
					@Override
					public void callback(TaskContext task) {
						try {
							StackUtil.addVariable(stack, result, task.getResult());
						}
						catch (OperatingContextException x) {
							// NA
						}

						try {
							stack.setState(ExecuteState.RESUME);

							// local context here is same as instruction, not as the sub task
							OperationContext.getAsTaskOrThrow().resume();
						}
						catch (Exception x) {
							Logger.error("Unable to resume after TaskHelper inst: " + x);
						}
					}
				});

				return ReturnOption.AWAIT;
			}
			else {
				// background, do not wait
				TaskHub.submit(task);

				return ReturnOption.CONTINUE;
			}
		}

		return ReturnOption.CONTINUE;
	}
}
