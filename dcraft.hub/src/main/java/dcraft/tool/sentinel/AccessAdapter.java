package dcraft.tool.sentinel;

import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
import dcraft.hub.op.OperationOutcomeEmpty;
import dcraft.hub.op.OperationOutcomeRecord;
import dcraft.interchange.icontact.IContactUtil;
import dcraft.log.Logger;
import dcraft.script.StackUtil;
import dcraft.script.work.ExecuteState;
import dcraft.script.work.ReturnOption;
import dcraft.script.work.StackWork;
import dcraft.struct.ListStruct;
import dcraft.struct.RecordStruct;
import dcraft.struct.scalar.NullStruct;
import dcraft.util.StringUtil;
import dcraft.xml.XElement;

public class AccessAdapter extends RecordStruct {

	@Override
	public ReturnOption operation(StackWork state, XElement code) throws OperatingContextException {
		if ("GetUsersV1".equals(code.getName())) {
			String name = StackUtil.stringFromElement(state, code, "Result");

			ListStruct users = AccessUtil.getUsersV1();

			if (StringUtil.isNotEmpty(name))
				StackUtil.addVariable(state, name, users);

			return ReturnOption.CONTINUE;
		}

		if ("GetUserIPRulesV1".equals(code.getName())) {
			String name = StackUtil.stringFromElement(state, code, "Result");
			Long userId = StringUtil.parseInt(StackUtil.stringFromElement(state, code, "UserId"));

			if (userId != null) {
				ListStruct users = AccessUtil.getUserIPRulesV1(userId);

				if (StringUtil.isNotEmpty(name))
					StackUtil.addVariable(state, name, users);
			}
			else {
				Logger.error(1, "No user provided");
			}

			return ReturnOption.CONTINUE;
		}

		if ("UpdateUserAccessV1".equals(code.getName())) {
			String ipv4 = StackUtil.stringFromElement(state, code, "IPV4");
			String desc = StackUtil.stringFromElement(state, code, "Description");
			Long ruleId = StringUtil.parseInt(StackUtil.stringFromElement(state, code, "RuleId"));

			if ((ruleId != null) && StringUtil.isNotEmpty(ipv4) && StringUtil.isNotEmpty(desc)) {
				System.out.println("update: " + ruleId + " - " + ipv4 + " - " + desc);

				AccessUtil.updateUserAccessV1(ipv4, ruleId, desc,
						new OperationOutcomeEmpty() {
							@Override
							public void callback() throws OperatingContextException {
								state.setState(ExecuteState.DONE);
								OperationContext.getAsTaskOrThrow().resume();
							}
						});

				return ReturnOption.AWAIT;
			}
			else {
				Logger.error(1, "No user provided");

				return ReturnOption.CONTINUE;
			}
		}

		return super.operation(state, code);
	}
}
