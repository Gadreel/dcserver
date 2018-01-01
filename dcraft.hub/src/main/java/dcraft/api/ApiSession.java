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
package dcraft.api;

import java.util.concurrent.CountDownLatch;

import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationOutcomeEmpty;
import dcraft.hub.op.OperationOutcomeStruct;
import dcraft.log.Logger;
import dcraft.script.work.ReturnOption;
import dcraft.service.IServiceRequestBuilder;
import dcraft.service.ServiceRequest;
import dcraft.stream.StreamFragment;
import dcraft.struct.RecordStruct;
import dcraft.struct.Struct;
import dcraft.task.IParentAwareWork;
import dcraft.util.cb.TimeoutPlan;
import dcraft.xml.XElement;

// TODO make some of the properties accessible via RecordStruct fields for dcScript
abstract public class ApiSession extends RecordStruct implements AutoCloseable {
	protected String sessionid = null;
	protected String nodeid = null;
	protected OperationOutcomeStruct lastResult = null;
	protected String domain = null;

	public String getSessionId() {
		return this.sessionid;
	}

	public String getNodeId() {
		return this.nodeid;
	}
	
	public void call(IServiceRequestBuilder builder) {
		this.call(builder.toServiceRequest());
	}
	
	abstract public void call(ServiceRequest request);
	abstract public void transfer(String channel, StreamFragment source, StreamFragment dest, OperationOutcomeEmpty callback);
	
	public boolean signin(String user, String pass) {
		ServiceRequest request = ServiceRequest.of("dcCoreServices", "Authentication", "SignIn")
				.withData(RecordStruct.record()
						.with("Username", user)
						.with("Password", pass)
				);
		
		try {
			OperationOutcomeStruct outcome = this.callWait(request);
			
			return ! outcome.hasErrors();
		}
		catch (OperatingContextException x) {
			Logger.error("Missing local context in API call: " + x);
		}
		
		return false;
	}
	
	public OperationOutcomeStruct callWait(ServiceRequest request) throws OperatingContextException {
		return this.callWait(request, TimeoutPlan.Regular);
	}
	
	public OperationOutcomeStruct callWait(ServiceRequest request, TimeoutPlan timeoutPlan) throws OperatingContextException {
		CountDownLatch latch = new CountDownLatch(1);
		
		this.lastResult = new OperationOutcomeStruct(timeoutPlan) {
			@Override
			public void callback(Struct result) throws OperatingContextException {
				latch.countDown();
			}
		};
		
		request.withOutcome(this.lastResult);
		
		this.call(request);
		
		try {
			latch.await();
		}
		catch (InterruptedException x) {
			Logger.errorTr(445, x);
		}
		
		return this.lastResult;
	}
	
	public void transferUp(String channel, StreamFragment source, OperationOutcomeEmpty callback) {
		this.transfer(channel, source, null, callback);
	}
	
	public void transferDown(String channel, StreamFragment dest, OperationOutcomeEmpty callback) {
		this.transfer(channel, null, dest, callback);
	}
	
	public void stop() {
		this.stopped();
	}

	abstract public void stopped();
	
	@Override
	public ReturnOption operation(IParentAwareWork stack, XElement code) throws OperatingContextException {
		if ("Stop".equals(code.getName())) {
			this.stop();
			
			return ReturnOption.CONTINUE;
		}
		
		return super.operation(stack, code);
	}
	
	@Override
	public void close() {
		this.stop();
	}
}
