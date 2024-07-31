package dcraft.core.doc;

import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
import dcraft.log.Logger;
import dcraft.script.StackUtil;
import dcraft.script.work.ExecuteState;
import dcraft.script.work.ReturnOption;
import dcraft.script.work.StackWork;
import dcraft.struct.RecordStruct;
import dcraft.struct.Struct;
import dcraft.task.Task;
import dcraft.task.TaskContext;
import dcraft.task.TaskHub;
import dcraft.task.TaskObserver;
import dcraft.util.StringUtil;
import dcraft.xml.XElement;

public class DocAdapter extends RecordStruct {
	@Override
	public ReturnOption operation(StackWork stack, XElement code) throws OperatingContextException {
		if ("RenderDoc".equals(code.getName())) {
			String domain = Struct.objectToString(StackUtil.refFromElement(stack, code, "Domain", true));
			String path = Struct.objectToString(StackUtil.refFromElement(stack, code, "Path", true));
			String handle = Struct.objectToString(StackUtil.stringFromElement(stack, code, "Result"));

			if (StringUtil.isEmpty(domain)) {
				Logger.error("Missing doc domain");
			}
			else if (StringUtil.isEmpty(path)) {
				Logger.error("Missing doc path");
			}
			else {
				TaskHub.submit(
						Task.ofSubContext()
								.withTitle("document builder builder")
								.withParams(RecordStruct.record()
										.with("Domain", domain)
										.with("Path", path)
								)
								.withWork(new DocRequestWork()),
						new TaskObserver() {
							@Override
							public void callback(TaskContext task) {
								try {
									RecordStruct resp = Struct.objectToRecord(task.getResult());

									if (StringUtil.isNotEmpty(handle))
										StackUtil.addVariable(stack, handle, resp);

									stack.setState(ExecuteState.DONE);

									OperationContext.getAsTaskOrThrow().resume();
								}
								catch (OperatingContextException x) {
									System.out.println("bad doc context: " + x);
								}
							}
						}
				);

				return ReturnOption.AWAIT;
			}

			return ReturnOption.CONTINUE;
		}

		return super.operation(stack, code);
	}
}
