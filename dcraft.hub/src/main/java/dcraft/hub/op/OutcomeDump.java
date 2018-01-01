package dcraft.hub.op;

import dcraft.struct.CompositeStruct;
import dcraft.struct.ListStruct;
import dcraft.struct.Struct;
import dcraft.util.cb.TimeoutPlan;

/**
 */
public class OutcomeDump extends OperationOutcomeStruct {
	static public OutcomeDump dump(String label) throws OperatingContextException {
		OutcomeDump out = new OutcomeDump();
		out.label = label;
		return out;
	}
	
	public OutcomeDump() throws OperatingContextException {
		super();
	}
	
	public OutcomeDump(TimeoutPlan plan) throws OperatingContextException {
		super(plan);
	}
	
	protected String label = null;
	
	@Override
	public void callback(Struct result) throws OperatingContextException {
		ListStruct msgs = this.getMessages();
		
		if (this.hasErrors()) {
			System.out.println(this.label + " has errors");
			
			if (msgs != null)
				System.out.println(msgs.toPrettyString());
		}
		else  {
			System.out.println(this.label + " Messages:");
			
			if (msgs != null)
				System.out.println(msgs.toPrettyString());
			
			System.out.println();
			
			System.out.println(this.label + " Response:");
			
			if (result instanceof CompositeStruct)
				System.out.println(((CompositeStruct) result).toPrettyString());
			else if (result != null)
				System.out.println(result.toString());
			else
				System.out.println("[empty]");
		}
	}
}
