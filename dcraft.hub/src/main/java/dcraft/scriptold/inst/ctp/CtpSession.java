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

import dcraft.api.ApiSession;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
import dcraft.hub.op.OperationObserver;
import dcraft.log.Logger;
import dcraft.scriptold.ExecuteState;
import dcraft.scriptold.StackEntry;
import dcraft.scriptold.inst.With;
import dcraft.tenant.Tenant;
import dcraft.tenant.TenantHub;
import dcraft.util.StringUtil;

public class CtpSession extends With {
	@Override
	public void prepTarget(StackEntry stack) throws OperatingContextException {
        String name = stack.stringFromSource("Name");            
        String host = stack.stringFromSource("Host");
        String user = stack.stringFromSource("User");
        String pwd = stack.stringFromSource("Password");
        
        if (StringUtil.isEmpty(name)) {
			stack.setState(ExecuteState.Done);
			Logger.errorTr(527);
			stack.resume();
			return;
        }
        
        if (StringUtil.isEmpty(host)) {
			stack.setState(ExecuteState.Done);
			Logger.errorTr(528);
			stack.resume();
			return;
        }
        
        Tenant di = TenantHub.resolveTenant(host);
        
        ApiSession sess = null;
        
        // if we handle the domain then use local session
        if (di != null) {
        	// TODO create for a given site?
			/* TODO restore
        	Session session = Session.of("hub:", di.getAlias(), "root");
        	
        	SessionHub.register(session);
        	
        	sess = new LocalSession();
    		((LocalSession)sess).init(session, stack.getInstruction().getXml());
    		
    		// then use root user
        	if (StringUtil.isEmpty(user)) {
        		((LocalSession)sess).startSessionAsRoot();
        	}
        	else if (!sess.startSession(user, pwd)) {
        		sess.close();
        		
				stack.setState(ExecuteState.Done);
				Logger.errorTr(530);
				stack.resume();
				return;            		
        	}
        	*/
        }
        else {
        	if (StringUtil.isEmpty(user)) {
				stack.setState(ExecuteState.Done);
				Logger.errorTr(529);
				stack.resume();
				return;
        	}
        	
        	// TODO enhance this some
        	/* TODO restore
    		sess = new HyperSession();
    		((HyperSession)sess).init(stack.getInstruction().getXml());
    		
            if (!sess.startSession(user, pwd)) {
            	sess.close();
            	
				stack.setState(ExecuteState.Done);
				OperationContext.get().errorTr(530);
				stack.resume();
				return;
            }
            */
        }
        
        ApiSession fsess = sess;
        
        // this only works if we are in a task context, however this should be the case
        // so that is ok
		OperationContext.getOrThrow().getController().addObserver(new OperationObserver() {
			@Override
			public void completed(OperationContext ctx) {
				try {
					fsess.close();
				} 
				catch (Exception x) {
					// TODO
				}
			}
		});
		
        stack.addVariable(name, sess);
        this.setTarget(stack, sess);
		
		this.nextOpResume(stack);
	}

	/*
	@Override
	public void run(final StackEntry stack) {
		if (stack.getState() == ExecuteState.Ready) {
            String name = stack.stringFromSource("Name");            
            String host = stack.stringFromSource("Host");
            String user = stack.stringFromSource("User");
            String pwd = stack.stringFromSource("Password");
            
            if (StringUtil.isEmpty(name)) {
				stack.setState(ExecuteState.Exit);
				OperationContext.get().errorTr(527);
				stack.resume();
				return;
            }
            
            if (StringUtil.isEmpty(host)) {
				stack.setState(ExecuteState.Exit);
				OperationContext.get().errorTr(528);
				stack.resume();
				return;
            }
            
            DomainInfo di = Hub.instance.resolveDomainInfo(host);
            
            ApiSession sess = null;
            
            // if we handle the domain then use local session
            if (di != null) {
            	Session session = Hub.instance.getSessions().create("hub:", host);
            	sess = new LocalSession();
        		((LocalSession)sess).init(session, stack.getInstruction().getXml());
        		
        		// then use root user
            	if (StringUtil.isEmpty(user)) {
            		((LocalSession)sess).startSessionAsRoot();
            	}
            	else if (!sess.startSession(user, pwd)) {
    				stack.setState(ExecuteState.Exit);
    				OperationContext.get().errorTr(530);
    				stack.resume();
    				return;            		
            	}
            }
            else {
            	if (StringUtil.isEmpty(user)) {
    				stack.setState(ExecuteState.Exit);
    				OperationContext.get().errorTr(529);
    				stack.resume();
    				return;
            	}
            	
            	// TODO enhance this some
        		sess = new WebSession();
        		((WebSession)sess).init(stack.getInstruction().getXml());
        		
                if (!sess.startSession(user, pwd)) {
    				stack.setState(ExecuteState.Exit);
    				OperationContext.get().errorTr(530);
    				stack.resume();
    				return;
                }
            }
    		
            stack.addVariable(name, sess);

			stack.getStore().setField("CurrNode", 0);
			stack.getStore().setField("Target", sess);
			stack.setState(ExecuteState.Resume);
			
			stack.resume();
		}		
		else
			super.run(stack);
	}
	*/
}
