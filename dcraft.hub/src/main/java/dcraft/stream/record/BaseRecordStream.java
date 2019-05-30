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
package dcraft.stream.record;

import java.util.ArrayList;
import java.util.List;

import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
import dcraft.stream.BaseStream;
import dcraft.stream.IStreamDown;
import dcraft.stream.ReturnOption;
import dcraft.struct.RecordStruct;
import dcraft.task.IParentAwareWork;
import dcraft.xml.XElement;

abstract public class BaseRecordStream extends BaseStream {
	protected List<RecordStruct> outslices = new ArrayList<>();
	protected IRecordStreamConsumer consumer = null;
	
	@Override
	public void setDownstream(IStreamDown<?> downstream) {
		if (downstream instanceof IRecordStreamConsumer)
			this.consumer = (IRecordStreamConsumer) downstream;
		
		super.setDownstream(downstream);
	}
	
	@Override
	public void init(IParentAwareWork stack, XElement el) {
	}
	
	@Override
	public void close() throws OperatingContextException {
    	this.outslices.clear();
    
    	super.close();
	}
	
	public void addSlice(RecordStruct rec) {
		this.outslices.add(rec);
	}

	public ReturnOption handlerFlush() throws OperatingContextException {
		if (OperationContext.getAsTaskOrThrow().isComplete())
			return ReturnOption.DONE;
		
		// write all messages in the queue
		while (this.outslices.size() > 0) {
			RecordStruct slice = this.outslices.remove(0);
			
			ReturnOption ret = this.consumer.handle(slice);
			
			if (ret != ReturnOption.CONTINUE)
				return ret;
		}
    	
       	return ReturnOption.CONTINUE;
	}
}
