package dcraft.interchange.taxjar;

import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
import dcraft.hub.op.OperationOutcome;
import dcraft.hub.op.OperationOutcomeRecord;
import dcraft.interchange.authorize.AuthUtil;
import dcraft.interchange.authorize.AuthUtilXml;
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

import java.math.BigDecimal;

public class TaxJarAdapter extends RecordStruct {

	@Override
	public ReturnOption operation(StackWork state, XElement code) throws OperatingContextException {
		// TODO future support Auth and capture separately, support getting settlement batches

		if ("Lookup".equals(code.getName())) {
			BaseStruct tx = StackUtil.refFromElement(state, code, "Value", true);
			String altConfig = StackUtil.stringFromElement(state, code, "AltConfig");
			String name = StackUtil.stringFromElement(state, code, "Result");

			//System.out.println(tx);

			BaseStruct result = TaxJarUtil.lookupTaxSync(Struct.objectToRecord(tx), altConfig);

			if (result == null)
				result = NullStruct.instance;

			StackUtil.addVariable(state, name, result);

			return ReturnOption.CONTINUE;
		}

		if ("Record".equals(code.getName())) {
			BaseStruct tx = StackUtil.refFromElement(state, code, "Value", true);
			String altConfig = StackUtil.stringFromElement(state, code, "AltConfig");
			String name = StackUtil.stringFromElement(state, code, "Result");

			//System.out.println(tx);

			BaseStruct result = TaxJarUtil.createTxSync(Struct.objectToRecord(tx), altConfig);

			if (result == null)
				result = NullStruct.instance;

			StackUtil.addVariable(state, name, result);

			return ReturnOption.CONTINUE;
		}

		if ("Refund".equals(code.getName())) {
			BaseStruct tx = StackUtil.refFromElement(state, code, "Value", true);
			String altConfig = StackUtil.stringFromElement(state, code, "AltConfig");
			String name = StackUtil.stringFromElement(state, code, "Result");

			//System.out.println(tx);

			BaseStruct result = TaxJarUtil.refundTxSync(Struct.objectToRecord(tx), altConfig);

			if (result == null)
				result = NullStruct.instance;

			StackUtil.addVariable(state, name, result);

			return ReturnOption.CONTINUE;
		}

		return super.operation(state, code);
	}
}
