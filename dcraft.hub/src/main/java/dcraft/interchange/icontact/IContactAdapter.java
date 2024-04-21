package dcraft.interchange.icontact;

import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
import dcraft.hub.op.OperationOutcomeRecord;
import dcraft.interchange.taxjar.TaxJarUtil;
import dcraft.script.StackUtil;
import dcraft.script.work.ExecuteState;
import dcraft.script.work.ReturnOption;
import dcraft.script.work.StackWork;
import dcraft.struct.BaseStruct;
import dcraft.struct.RecordStruct;
import dcraft.struct.Struct;
import dcraft.struct.scalar.NullStruct;
import dcraft.util.StringUtil;
import dcraft.xml.XElement;

public class IContactAdapter extends RecordStruct {

	@Override
	public ReturnOption operation(StackWork state, XElement code) throws OperatingContextException {
		// TODO future support Auth and capture separately, support getting settlement batches

		if ("Subscribe".equals(code.getName())) {
			String altConfig = StackUtil.stringFromElement(state, code, "AltConfig");
			String email = StackUtil.stringFromElement(state, code, "Email");
			String first = StackUtil.stringFromElement(state, code, "FirstName");
			String last = StackUtil.stringFromElement(state, code, "LastName");
			String listid = StackUtil.stringFromElement(state, code, "ListId");
			String name = StackUtil.stringFromElement(state, code, "Result");

			RecordStruct contact = RecordStruct.record()
					.with("email", email)
					.with("firstName", first)
					.with("lastName", last);

			//System.out.println(tx);

			IContactUtil.subscribe(altConfig, contact, listid,
					new OperationOutcomeRecord() {
						@Override
						public void callback(RecordStruct result) throws OperatingContextException {
							if (this.isNotEmptyResult())
								System.out.println("got: " + result.toPrettyString());

							if (StringUtil.isNotEmpty(name)) {
								if (result == null)
									StackUtil.addVariable(state, name, NullStruct.instance);
								else
									StackUtil.addVariable(state, name, result);
							}

							state.setState(ExecuteState.DONE);
							OperationContext.getAsTaskOrThrow().resume();
						}
					});

			return ReturnOption.AWAIT;
		}

		return super.operation(state, code);
	}
}
