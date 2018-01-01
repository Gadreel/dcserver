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
package dcraft.script.work;

import dcraft.hub.op.OperatingContextException;
import dcraft.script.ScriptHub;
import dcraft.struct.Struct;
import dcraft.task.IParentAwareWork;
import dcraft.task.TaskContext;
import dcraft.xml.XElement;

public class OperationWork extends StackWork {
	static public OperationWork of(IParentAwareWork parent, XElement op) {
		OperationWork sw = new OperationWork();
		
		sw.parent = parent;
		sw.op = op;
		
		return sw;
	}
	
	protected XElement op = null;
	
	protected OperationWork() { }
	
	public XElement getOperation() {
		return this.op;
	}
	
	public OperationWork withOperation(XElement v) {
		this.op = v;
		return this;
	}
	
	// this approach is if we want to run a single instruction out of the blue, not in a Script
	// the instruction can either issue an AWAIT - if so it must then issue a resume and, when done
	// must set the ExecuteState to Done. So if Done after an AWAIT then exit
	@Override
	public void run(TaskContext taskctx) throws OperatingContextException {
		if (this.state == ExecuteState.DONE)
			taskctx.returnResult();
		else if (this.run(taskctx.getResult()) != ReturnOption.AWAIT)
			taskctx.returnResult();
	}
	
	// this is called from within the Script context, bypassing the std run ctx above
	public ReturnOption run(Struct target) throws OperatingContextException {
		if ((op == null) || (this.state == ExecuteState.DONE))
			return ReturnOption.CONTINUE;
		
		ReturnOption ret = ScriptHub.operation(this, target, this.op);
		
		// always mark done - operations cannot be resumed
		//if (ret != ReturnOption.AWAIT)
		this.setState(ExecuteState.DONE);
		
		return ret;
	}
	
	@Override
	public void cancel() {
		// TODO enable - maybe set a flag in Store?
	}
}
