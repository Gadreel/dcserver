package dcraft.script.inst;

import dcraft.db.request.query.CollectorField;
import dcraft.db.request.query.SelectDirectRequest;
import dcraft.db.request.query.SelectFields;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
import dcraft.hub.op.OperationOutcomeStruct;
import dcraft.script.StackUtil;
import dcraft.script.work.ExecuteState;
import dcraft.script.work.InstructionWork;
import dcraft.script.work.ReturnOption;
import dcraft.service.ServiceHub;
import dcraft.struct.BaseStruct;
import dcraft.struct.ListStruct;
import dcraft.struct.Struct;
import dcraft.struct.scalar.NullStruct;
import dcraft.task.TaskContext;
import dcraft.util.StringUtil;
import dcraft.xml.XElement;

public class Sort extends Instruction {
	static public Sort tag() {
		Sort el = new Sort();
		el.setName("dcs.Sort");
		return el;
	}
	
	@Override
	public XElement newNode() {
		return Sort.tag();
	}
	
	/*
	<dcs.Sort Target="$users" ByField="LastName">
	 */
	
	// TODO add error checking
	
	@Override
	public ReturnOption run(InstructionWork state) throws OperatingContextException {
		if (state.getState() == ExecuteState.READY) {
			BaseStruct target = StackUtil.refFromSource(state, "Target");

			if (target != null) {
				if (target instanceof ListStruct) {
					ListStruct list = (ListStruct) target;

					String field = StackUtil.stringFromSource(state, "ByField");

					if (StringUtil.isNotEmpty(field)) {
						// TODO remove this --
					}
				}
			}
		}
		
		return ReturnOption.CONTINUE;
	}
}
