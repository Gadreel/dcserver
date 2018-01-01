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

import java.io.IOException;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;

import dcraft.filestore.FileDescriptor;
import dcraft.hub.app.ApplicationHub;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
import dcraft.scriptold.StackEntry;
import dcraft.stream.ReturnOption;
import dcraft.util.FileUtil;
import dcraft.util.StringUtil;
import dcraft.util.io.CyclingByteBufferOutputStream;
import dcraft.xml.XElement;

public class TarStream extends TransformFileStream {
    protected CyclingByteBufferOutputStream bstream = null;
    protected TarArchiveOutputStream tstream = null;
    protected boolean archiveopenflag = false;
    protected boolean finalflag = false;
    protected String nameHint = null;
    protected String lastpath = null;
    
    public TarStream() {
    }
    
    public TarStream withNameHint(String v) {
    	this.nameHint = v;
    	return this;
    }

	@Override
	public void init(StackEntry stack, XElement el) {
		this.nameHint = stack.stringFromElement(el, "NameHint");
	}

	@Override
    public void close() throws OperatingContextException {
		//System.out.println("Tar killed");	// TODO
		
    	if (this.tstream != null)
			try {
				this.tstream.close();
			} 
    		catch (IOException x) {
			}
    	
    	this.bstream = null;
    	this.tstream = null;
    
    	super.close();
    }
    
	// make sure we don't return without first releasing the file reference content
	@Override
	public ReturnOption handle(FileSlice slice) throws OperatingContextException {
    	if (slice == FileSlice.FINAL) {
    		if (this.tstream == null) 
        		return this.consumer.handle(slice);
    			
    		this.finalflag = true;
    	}
    	
		// TODO add support this.tabulator
    	
    	// I don't think tar cares about folder entries at this stage - tar is for file content only
    	// folder scanning is upstream in the FileSourceStream and partners
    	// TODO try with ending / to file name
    	if ((slice.file != null) && slice.file.isFolder())
    		return ReturnOption.CONTINUE;
    	
    	// init if not set for this round of processing 
    	if (this.tstream == null) {
    		this.bstream = new CyclingByteBufferOutputStream();
            this.tstream = new TarArchiveOutputStream(this.bstream);
            this.tstream.setLongFileMode(TarArchiveOutputStream.LONGFILE_GNU);
    	}
        
    	ByteBuf in = slice.data;
        ByteBuf out = null; 
    	
        // always allow for a header (512) and/or footer (1024) in addition to content
        int sizeEstimate = (in != null) ? in.readableBytes() + 2048 : 2048;
        out = ApplicationHub.getBufferAllocator().heapBuffer(sizeEstimate);
    	
        this.bstream.installBuffer(out);
        
        // TODO if there is no output available to send and not EOF then just request more,
        // no need to send a message that is empty and not EOF
        
        FileDescriptor blk = new FileDescriptor();
        
        FileSlice sliceout = FileSlice.allocate(blk, out, 0, false);
		
		if (StringUtil.isNotEmpty(this.lastpath)) {
			blk.withPath(this.lastpath);
		}
		else {
			if (StringUtil.isNotEmpty(this.nameHint))
				this.lastpath = "/" + this.nameHint + ".tar";
			else if ((slice.file != null) && StringUtil.isNotEmpty(slice.file.getName()))
				this.lastpath = "/" + (StringUtil.isNotEmpty(this.nameHint) ? this.nameHint : slice.file.getName()) + ".tar";
			else
				this.lastpath = "/" + FileUtil.randomFilename() + ".tar";
			
			blk.withPath(this.lastpath);
		}
		
		blk.withModificationTime(ZonedDateTime.now(ZoneId.of("UTC")));
    	
    	if ((slice.file != null) && !this.archiveopenflag && !this.finalflag) {
    		TarArchiveEntry tentry = new TarArchiveEntry(slice.file.getPath().toString().substring(1), true);		
    		tentry.setSize(slice.file.getSize());
    		tentry.setModTime(slice.file.getModificationAsTime().toInstant().toEpochMilli());
    		
    		try {
				this.tstream.putArchiveEntry(tentry);
			} 
    		catch (IOException x) {
    			if (in != null)
    				in.release();
    			
				out.release();
				OperationContext.getAsTaskOrThrow().kill("Problem writing tar entry: " + x);
				return ReturnOption.DONE;
			}
    		
    		this.archiveopenflag = true;
    	}
		
    	if (in != null)
			try {
				this.tstream.write(in.array(), in.arrayOffset(), in.writerIndex());
			} 
			catch (IOException x) {
				in.release();
				out.release();
				OperationContext.getAsTaskOrThrow().kill("Problem writing tar body: " + x);
				return ReturnOption.DONE;
			}

        if (slice.isEof()) {
        	try {
		        this.tstream.closeArchiveEntry();
			} 
			catch (IOException x) {
    			if (in != null)
    				in.release();
    			
				out.release();
				OperationContext.getAsTaskOrThrow().kill("Problem closing tar entry: " + x);
				return ReturnOption.DONE;
			}
    		
    		this.archiveopenflag = false;
        }
    	
        if (in != null)
        	in.release();
        
    	if (slice == FileSlice.FINAL) {			
    		sliceout.setEof(true);
        	
        	try {
		        this.tstream.close();
			} 
			catch (IOException x) {
				//in.release();
				out.release();
				OperationContext.getAsTaskOrThrow().kill("Problem closing tar stream: " + x);
				return ReturnOption.DONE;
			}
        	
    		this.tstream = null;
    		this.bstream = null;
    	}
    	else
    		this.bstream.uninstallBuffer();		// we are done with out forever, don't reference it
		
		//System.out.println("tar sending: " + out.readableBytes());
		
		ReturnOption v = this.consumer.handle(sliceout);
       	
       	if (! this.finalflag)
       		return v;
       	
       	if (v == ReturnOption.CONTINUE)
    		return this.consumer.handle(FileSlice.FINAL);
    	
       	return ReturnOption.DONE;
    }
    
    @Override
    public void read() throws OperatingContextException {
    	if (this.finalflag) {
    		this.consumer.handle(FileSlice.FINAL);
    		return;
    	}
    	
    	this.upstream.read();
    }
}
