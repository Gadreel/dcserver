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

import dcraft.struct.scalar.BinaryStruct;
import dcraft.task.IParentAwareWork;
import io.netty.buffer.ByteBuf;
import dcraft.filestore.FileDescriptor;
import dcraft.hub.app.ApplicationHub;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
import dcraft.stream.IStreamSource;
import dcraft.stream.ReturnOption;
import dcraft.util.Memory;
import dcraft.util.StringUtil;
import dcraft.xml.XElement;

public class MemorySourceStream extends BaseFileStream implements IStreamSource {
	static public MemorySourceStream fromBinary(BinaryStruct v) {
		MemorySourceStream mss = new MemorySourceStream();
		mss.source = new Memory(v.getValue());
		return mss;
	}
	
	static public MemorySourceStream fromBinary(Memory v) {
		MemorySourceStream mss = new MemorySourceStream();
		mss.source = new Memory(v);
		return mss;
	}
	
	static public MemorySourceStream fromBinary(byte[] v) {
		MemorySourceStream mss = new MemorySourceStream();
		mss.source = new Memory(v);
		mss.source.setPosition(0);
		return mss;
	}
	
	static public MemorySourceStream fromChars(CharSequence v) {
		MemorySourceStream mss = new MemorySourceStream();
		mss.source = new Memory(v);
		mss.source.setPosition(0);
		return mss;
	}

	protected Memory source = null;	
	protected long inprog = 0;
	protected boolean eof = false;
	protected String fname = null;

	public MemorySourceStream withMemory(Memory v) {
		this.source = v;
		return this;
	}

	public MemorySourceStream withFilename(String v) {
		this.fname = v;
		return this;
	}
	
	// for use with dcScript
	@Override
	public void init(IParentAwareWork stack, XElement el) {
		// anything we need to gleam from the xml?
	}
	
	@Override
	public void close() throws OperatingContextException {
		this.source = null;
		
		super.close();
	}

	/**
	 * Someone downstream wants more data
	 */
	@Override
	public void read() throws OperatingContextException {
		if (this.source == null) {
			this.consumer.handle(FileSlice.FINAL);
			return;
		}
		
		if (this.inprog == 0) {
			// As a source we are responsible for progress tracking
			OperationContext.getAsTaskOrThrow().setAmountCompleted(0);
			this.currfile = new FileDescriptor();
	        this.currfile.withPath(StringUtil.isNotEmpty(this.fname) ? this.fname : "/memory.bin");
	        this.currfile.withSize(this.source.getLength());
		}
		else if (this.eof) {
			this.consumer.handle(FileSlice.FINAL);
			return;
		}
		
		while (true) {
			// TODO sizing?
	        ByteBuf data = ApplicationHub.getBufferAllocator().heapBuffer(32768);
	        
	        int amt = this.source.read(data.array(), data.arrayOffset(), data.capacity());
	
	        this.eof = this.source.getPosition() == this.source.getLength();
	        
	        data.writerIndex(amt);
	        
	        FileSlice sliceout = FileSlice.allocate(this.currfile, data, 0, this.eof);
	        
        	this.inprog += amt;
	        OperationContext.getAsTaskOrThrow().setAmountCompleted((int)(this.inprog * 100 / this.source.getLength()));
	        
	    	if (this.consumer.handle(sliceout) != ReturnOption.CONTINUE)
	    		break;

	    	if (this.eof) {
				this.consumer.handle(FileSlice.FINAL);
	    		break;
	    	}
		}
	}
}
