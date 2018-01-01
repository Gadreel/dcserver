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
package dcraft.stream.file;

import io.netty.buffer.ByteBuf;

import java.util.ArrayList;
import java.util.List;

import dcraft.filestore.FileDescriptor;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
import dcraft.stream.BaseStream;
import dcraft.stream.IStreamDown;
import dcraft.stream.ReturnOption;

abstract public class BaseFileStream extends BaseStream {
	protected List<FileSlice> outslices = new ArrayList<>();
	protected FileDescriptor currfile = null;
	protected IFileStreamConsumer consumer = null;
	
	@Override
	public void setDownstream(IStreamDown<?> downstream) {
		if (downstream instanceof IFileStreamConsumer)
			this.consumer = (IFileStreamConsumer) downstream;
		
		super.setDownstream(downstream);
	}
	
	@Override
	public void close() throws OperatingContextException {
		this.currfile = null;
		
    	// not truly thread safe, consider
    	for (FileSlice bb : this.outslices)
    		bb.release();
    	
    	this.outslices.clear();
    
    	super.close();
	}
	
	public void addSlice(ByteBuf buf, long offset, boolean eof) {
		FileSlice s = FileSlice.allocate(this.currfile, buf, offset, eof);

		this.outslices.add(s);
	}

	public ReturnOption handlerFlush() throws OperatingContextException {
		if (OperationContext.getAsTaskOrThrow().isComplete())
			return ReturnOption.DONE;
		
		// write all messages in the queue
		while (this.outslices.size() > 0) {
			FileSlice slice = this.outslices.remove(0);
			
			ReturnOption ret = this.consumer.handle(slice);
			
			if (ret != ReturnOption.CONTINUE)
				return ret;
		}
    	
       	return ReturnOption.CONTINUE;
	}
}
