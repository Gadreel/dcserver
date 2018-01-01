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
package dcraft.scriptold.inst.ctp;

import dcraft.hub.op.OperatingContextException;
import dcraft.scriptold.Instruction;
import dcraft.scriptold.StackEntry;

public class CtpCall extends Instruction {
	@Override
	public void run(StackEntry stack) throws OperatingContextException {
        /* TODO restore
        String name = stack.stringFromSource("Name");
        
        if (StringUtil.isEmpty(name))
        	name = "CtpCall_" + stack.getActivity().tempVarName();
        
        String vname = name;
        
        Struct ss = stack.refFromSource("Session");
        
        if ((ss == null) || !(ss instanceof ApiSession)) {
			stack.setState(ExecuteState.Done);
			Logger.errorTr(531);
        	stack.resume();
        	return;
        }
        
		ApiSession sess = (ApiSession) ss;
        
        Message msg = null;
        
        Struct ms = stack.refFromSource("Message");
        
        if ((ms != null) && !(ms instanceof NullStruct)) {
        	if (ms instanceof Message) {
        		msg = (Message) ms;
        	}
        	else if (ms instanceof RecordStruct) {
        		msg = MessageUtil.fromRecord((RecordStruct) ms);
        	} 
        	else {
    			stack.setState(ExecuteState.Done);
    			Logger.errorTr(532);
            	stack.resume();
            	return;
        	}
        }
        else {
            Struct bdy = stack.refFromSource("Data");
            String ser = stack.stringFromSource("Service");
            String feat = stack.stringFromSource("Feature");
            String op = stack.stringFromSource("Op");
        	
            msg = new Message(ser, feat, op, bdy);
        }
        
        if ((msg == null)) {
			stack.setState(ExecuteState.Done);
			Logger.errorTr(533);
        	stack.resume();
        	return;        	
        }
        
        if (stack.getInstruction().getXml().getName().equals("CtpCallForget")) {
        	sess.sendForgetMessage(msg);
			stack.setState(ExecuteState.Done);
			stack.resume();
        }
        else {
        	sess.sendMessage(msg, new OperationApiOutcome() {
				@Override
				public void callback(Message result) throws OperatingContextException {
					Struct rdata = NullStruct.instance;
					
					if ((result != null) && result.hasField("Body"))
						rdata = result.getField("Body");
		    		
		            stack.addVariable(vname, rdata);
					
					stack.setState(ExecuteState.Done);
					stack.resume();
				}
			});
        }
        */
	}
	
	@Override
	public void cancel(StackEntry stack) {
		// do nothing, this isn't cancellable
	}
}
