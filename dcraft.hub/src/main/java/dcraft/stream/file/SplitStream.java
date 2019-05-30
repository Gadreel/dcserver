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

import dcraft.filestore.FileDescriptor;
import dcraft.hub.op.OperatingContextException;
import dcraft.script.StackUtil;
import dcraft.stream.ReturnOption;
import dcraft.struct.scalar.IntegerStruct;
import dcraft.task.IParentAwareWork;
import dcraft.util.FileUtil;
import dcraft.util.StringUtil;
import dcraft.util.TimeUtil;
import dcraft.xml.XElement;
import io.netty.buffer.ByteBuf;

public class SplitStream extends TransformFileStream {
	protected int seqnum = 1;
	protected long size = 16 * 1024 * 1024;		// default 16 MB
	protected String template = "file-%seq%.bin";
	protected String namehint = null;
	protected boolean dashnummod = true;
	protected int seqpad = 4;
	
	protected int currchunk = 0;
	protected IntegerStruct countvar = null;
	
	public SplitStream withSize(long v) {
		this.size = v;
		return this;
	}
	
	// include a %seq% to be replaced, like this file-%seq%.bin
	public SplitStream withNameTemplate(String v) {
		this.template = v;
		return this;
	}
	
	public SplitStream withNameHint(String v) {
		this.namehint = v;
		return this;
	}
	
	public SplitStream withDashNumMode(boolean v) {
		this.dashnummod = v;
		return this;
	}
	
	public SplitStream withCountVar(IntegerStruct v) {
		this.countvar = v;
		return this;
	}
	
	public SplitStream() {
    }

	@Override
	public void init(IParentAwareWork stack, XElement el) throws OperatingContextException {
		this.seqnum = (int) StackUtil.intFromElement(stack, el, "StartAt", this.seqnum);
		
		String size = StackUtil.stringFromElement(stack, el, "Size", "10MB");
		
		this.size = FileUtil.parseFileSize(size);
		
		String temp = StackUtil.stringFromElement(stack, el, "Template");
		
		if (StringUtil.isNotEmpty(temp))
			this.template = temp;
	}
    
	// make sure we don't return without first releasing the file reference content
	@Override
	public ReturnOption handle(FileSlice slice) throws OperatingContextException {
    	if (slice == FileSlice.FINAL) {
			if (this.countvar != null)
				this.countvar.adaptValue(this.seqnum);
			
			return this.consumer.handle(slice);
		}
		
		// TODO add support this.tabulator

    	if (this.currfile == null) {
			this.currfile = this.buildCurrent(slice.file, false);
			
			if (this.countvar != null)
				this.countvar.adaptValue(this.seqnum);
		}
		
    	ByteBuf in = slice.data;

    	if (in != null) {
    		while (in.isReadable()) {
    			long amt = Math.min((long) in.readableBytes(), this.size - this.currchunk);
    			
    			// amt will always be within bounds of an int since readableBytes is
    			ByteBuf out = in.copy(in.readerIndex(), (int) amt);
    			
    			in.skipBytes((int) amt);
    			
    			this.currchunk += amt;
    		
    			boolean eof = (this.currchunk == this.size) || (! in.isReadable() && slice.isEof());
    			
    			this.addSlice(out, 0, eof); 
    			
    			if (eof) {
    				this.seqnum++;
    				
    				if (this.countvar != null)
    					this.countvar.adaptValue(this.seqnum);
    				
    				this.currchunk = 0;
    	    		this.currfile = this.buildCurrent(slice.file, eof);
    			}
			}
    		
    		in.release();
    	}
    	// TODO how is this useful?
    	else if (slice.isEof()) {
			this.addSlice(null, 0, false); 
    	}
    	
    	return this.handlerFlush();
    }
    
    public FileDescriptor buildCurrent(FileDescriptor curr, boolean eof) {
		// create the output message
    	FileDescriptor blk = new FileDescriptor();
		
        blk.withModificationTime(TimeUtil.now());
        
        if (this.dashnummod) {
        	String cpath = curr.getPath();
        	
        	/*
        	int idx = cpath.lastIndexOf('.');
        	
        	String bpath = cpath.substring(0, idx) + "-"
					+ StringUtil.leftPad(this.seqnum + "", this.seqpad, '0')
					+ cpath.substring(idx);
        	*/
        	
        	String bpath = cpath + "-" + StringUtil.leftPad(this.seqnum + "", this.seqpad, '0');
        	
        	blk.withPath(bpath);
		}
		else {
			// keep the path, just vary the name to the template
			blk.withPath(curr.getPathAsCommon().resolvePeer("/" + this.template.replace("%seq%",
					StringUtil.leftPad(this.seqnum + "", this.seqpad, '0'))));
		}
		
        if (eof)
        	blk.withSize(this.currchunk);
        else
        	blk.withSize(0);						// don't know yet
        
        return blk;
    }
}
