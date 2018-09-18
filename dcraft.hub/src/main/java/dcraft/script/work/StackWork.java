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

import dcraft.struct.ListStruct;
import dcraft.struct.RecordStruct;
import dcraft.task.*;

abstract public class StackWork implements IParentAwareWork, IDebuggableWork, ICancelAwareWork {
	protected ExecuteState state = ExecuteState.READY;
	protected IParentAwareWork parent = null;
	protected RecordStruct store = new RecordStruct();
	protected boolean isContinue = false;
	
	protected StackWork() { }
	
	@Override
    public IParentAwareWork getParent() {
    	return this.parent;
    }

	@Override
	public StackWork withParent(IParentAwareWork v) {
		this.parent = v;
		return this;
	}

	public boolean checkIsContinue() {
		boolean ret = this.isContinue;
		this.isContinue = false;
		return ret;
	}

	public StackWork withContinueFlag() {
		this.isContinue = true;
		this.state = ExecuteState.RESUME;
		return this;
	}

	public RecordStruct getStore() {
		return this.store;
	}
	
	public ExecuteState getState() {
		return (this.state != null) ? this.state : ExecuteState.DONE;
	}
	
	public void setState(ExecuteState v) {
		this.state = v;
	}
		
    @Override
    public void debugStack(ListStruct dumpList) {
    	RecordStruct dumpRec = new RecordStruct();
    	dumpList.withItem(dumpRec);
    	
    	this.collectDebugRecord(dumpRec);
    }
    
    @Override
    public void collectDebugRecord(RecordStruct rec) {
    }
}
