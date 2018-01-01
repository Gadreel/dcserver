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
package dcraft.scriptold;

import dcraft.hub.op.OperatingContextException;
import dcraft.struct.RecordStruct;
import dcraft.struct.Struct;
import dcraft.struct.scalar.NullStruct;
import dcraft.xml.XElement;
import dcraft.xml.XNode;

// for instructions that are composed of multiple operations
abstract public class Ops extends Instruction {
	@Override
	public void run(StackEntry stack) throws OperatingContextException {
		if (stack.getState() == ExecuteState.Ready) {
			stack.getStore().with("SubAlign", true);
			stack.getStore().with("CurrNode", 0);
			stack.getStore().with("Target", NullStruct.instance);
			stack.setState(ExecuteState.Resume);
			this.prepTarget(stack);
		}
		else if (stack.getState() == ExecuteState.Resume) {
			if (stack.getStore().getFieldAsBooleanOrFalse("SubAlign")) {
				this.nextOpResume(stack);
				return;
			}

			// next op needs to align
			stack.getStore().with("SubAlign", true);
			
			Struct var2 = (Struct) stack.getStore().getField("Target");
			int cnode = stack.getStore().getFieldAsInteger("CurrNode").intValue();
			XNode nod = this.source.getChild(cnode - 1);
			
			this.runOp(stack, (XElement)nod, var2);
		}
	}
	
	public void nextOpResume(StackEntry stack) throws OperatingContextException {
		// next op needs to run
		stack.getStore().with("SubAlign", false);
		
		while (true) {
			int cnode = stack.getStore().getFieldAsInteger("CurrNode").intValue();
			
			if (cnode >= this.source.children()) {
				stack.setState(ExecuteState.Done);
				break;
			}
			
			XNode nod = this.source.getChild(cnode);
			stack.getStore().with("CurrNode", cnode + 1);
			
			if (nod instanceof XElement) {
				stack.setState(ExecuteState.Resume);
				break;
			}
		} 
		
		stack.resume();
	}
	
	public void setTarget(StackEntry stack, Struct v) throws OperatingContextException {
		stack.getStore().with("Target", v);
		stack.setLastResult(v);
	}
	
	// subclass is responsible for resuming stack  
	abstract public void prepTarget(StackEntry stack) throws OperatingContextException;
	
	// subclass is responsible for resuming stack  
	abstract public void runOp(StackEntry stack, XElement op, Struct target) throws OperatingContextException;

	@Override
	public RecordStruct collectDebugRecord(StackEntry stack, RecordStruct rec) {
		RecordStruct sub = super.collectDebugRecord(stack, rec);

		if (stack.getState() == ExecuteState.Resume) {
			int cnode = stack.getStore().getFieldAsInteger("CurrNode").intValue() - 1;
			
			if (cnode >= this.source.children()) 
				return null;
			
			XNode nod = this.source.getChild(cnode);
			
			if (! (nod instanceof XElement)) 
				return null;

			XElement mut = (XElement)nod;
			
			sub = new RecordStruct();
			sub.with("Line", mut.getLine());
			sub.with("Column", mut.getCol());		
			sub.with("Command", mut.toLocalString());
		   	return sub;
		}
		
		return sub;
	}
}
