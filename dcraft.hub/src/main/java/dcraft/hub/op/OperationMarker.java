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
package dcraft.hub.op;

import dcraft.log.DebugLevel;
import dcraft.log.Logger;
import dcraft.struct.ListStruct;

/**
 * Provides info about the success of a method call - like a boolean return - only with more info.
 * Check "hasErrors" to see if call failed.  Also there is an error code, and a log of messages.
 * That log of messages will also get written to the debug logger using the TaskContext of the caller.
 * It is also possible to track progress using a step count and/or a percent count.
 * 
 * @author Andy
 *
 */
public class OperationMarker implements AutoCloseable {  
	static public OperationMarker create() {
		return OperationMarker.create(OperationContext.getOrNull());
	}
	
	static public OperationMarker clearErrors() {
		OperationMarker om = OperationMarker.create(OperationContext.getOrNull());
		om.clearmode = true;
		return om;
	}
	
	static public OperationMarker create(OperationContext ctx) {
		OperationMarker om = new OperationMarker();
		
		if (ctx == null) {
			Logger.error("Allocating an Operation Marker where no context exists.");
			return null;
		}

		om.contextid = ctx.getOpId();
		om.msgStart = ctx.getController().logMarker();
		
		return om;
	}
	
	protected String contextid = null;
	protected int msgStart = 0;		// start of messages
	protected int msgEnd = -1;		// all messages
	protected boolean clearmode = false;
    
	public int getMsgStart() {
		return this.msgStart;
	}
	
	public int getMsgEnd() {
		return this.msgEnd;
	}
    
    protected OperationMarker() {
    }
    
    public OperationContext getOperationContext() throws OperatingContextException {
    	OperationContext ctx = OperationContext.getOrNull();
		
		if (ctx == null) 
			throw new OperatingContextException("Operation Marker has no context.");

		if (! ctx.getOpId().equals(this.contextid))
			throw new OperatingContextException("Accessing Operation Marker while in the wrong context.");
		
		return ctx;
    }
    
    /**
     * Only call this on the correct context, otherwise you may end up with the wrong position or no position
     */
    @Override
    public void close() throws OperatingContextException {
    	OperationContext ctx = this.getOperationContext();
		
		this.msgEnd = ctx.getController().logMarker();		// end is exclusive, so size is right
		
		if (this.clearmode)
			this.downgradeErrors();
    }
    	
	/**
	 * @return all messages logged with this call (and possibly sub calls made within the call)
	 */
	public ListStruct getMessages() throws OperatingContextException {
		return this.getOperationContext().getController().getMessages(this.msgStart, this.msgEnd);
	}
		
	/* TODO move to utility
	 * @return create a message for the bus that holds this result, useful for service results
	 *
	public Message toLogMessage() throws OperatingContextException {
		Message m = new Message();
		
		m.withField("Messages", this.getMessages());		
		
		return m;
	}
	*/

	/**
	 * @return true if a relevant error code is present
	 */
	public boolean hasErrors() throws OperatingContextException {
		return this.getOperationContext().getController().hasLevel(this.msgStart, this.msgEnd, DebugLevel.Error);
	}

	public void downgradeErrors() throws OperatingContextException {
		this.getOperationContext().getController().downgradeErrors(this.msgStart, this.msgEnd);
	}

	/**
	 * @param code to search for
	 * @return true if an error code is present
	 */
	public boolean hasCode(long code) throws OperatingContextException {
		return this.getOperationContext().getController().hasCode(code, this.msgStart, this.msgEnd);
	}

	public boolean hasCodeRange(long from, long to) throws OperatingContextException {
		return this.getOperationContext().getController().hasCodeRange(from, to, this.msgStart, this.msgEnd);
	}

	public boolean hasLogLevel(DebugLevel lvl) throws OperatingContextException {
		return this.getOperationContext().getController().hasLevel(this.msgStart, this.msgEnd, lvl);
	}
}
