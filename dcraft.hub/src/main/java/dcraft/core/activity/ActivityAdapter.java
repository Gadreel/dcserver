package dcraft.core.activity;

import dcraft.hub.op.OperatingContextException;
import dcraft.script.StackUtil;
import dcraft.script.work.ReturnOption;
import dcraft.script.work.StackWork;
import dcraft.struct.RecordStruct;
import dcraft.struct.Struct;
import dcraft.struct.scalar.NullStruct;
import dcraft.struct.scalar.StringStruct;
import dcraft.util.StringUtil;
import dcraft.xml.XElement;

public class ActivityAdapter extends RecordStruct {
	@Override
	public ReturnOption operation(StackWork stack, XElement code) throws OperatingContextException {
		if ("RecordUserActivity".equals(code.getName())) {
			String channel = Struct.objectToString(StackUtil.refFromElement(stack, code, "Channel", true));
			String address = Struct.objectToString(StackUtil.refFromElement(stack, code, "Address", true));
			String userId = Struct.objectToString(StackUtil.refFromElement(stack, code, "UserId", true));
			String target = Struct.objectToString(StackUtil.refFromElement(stack, code, "Target", true));
			String contextname = Struct.objectToString(StackUtil.refFromElement(stack, code, "Name", true));
			RecordStruct contextdata = Struct.objectToRecord(StackUtil.refFromElement(stack, code, "Data", true));
			String note = Struct.objectToString(StackUtil.refFromElement(stack, code, "Note", true));
			String handle = Struct.objectToString(StackUtil.stringFromElement(stack, code, "Result"));

			String actid = ActivityUtil.recordUserActivity(channel, address, userId, target, contextname, contextdata, note);

			if (StringUtil.isNotEmpty(handle)) {
				if (StringUtil.isEmpty(actid))
					StackUtil.addVariable(stack, handle, NullStruct.instance);
				else
					StackUtil.addVariable(stack, handle, StringStruct.of(actid));
			}

			return ReturnOption.CONTINUE;
		}

		return super.operation(stack, code);
	}
}
