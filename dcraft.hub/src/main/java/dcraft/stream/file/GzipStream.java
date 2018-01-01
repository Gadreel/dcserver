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

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.zip.CRC32;
import java.util.zip.Deflater;

import org.apache.commons.compress.compressors.gzip.GzipUtils;

import dcraft.filestore.FileDescriptor;
import dcraft.hub.app.ApplicationHub;
import dcraft.hub.op.OperatingContextException;
import dcraft.scriptold.StackEntry;
import dcraft.stream.ReturnOption;
import dcraft.util.FileUtil;
import dcraft.util.StringUtil;
import dcraft.xml.XElement;

public class GzipStream extends TransformFileStream {
	static public GzipStream create() {
		return new GzipStream(); 
	}
	
    protected static final byte[] gzipHeader = {0x1f, (byte) 0x8b, Deflater.DEFLATED, 0, 0, 0, 0, 0, 0, 0};
    
    protected int compressionLevel = 6;
    
    protected Deflater deflater = null;
    protected CRC32 crc = new CRC32();
    protected boolean writeHeader = true;
    
    protected String nameHint = null;
    protected String lastpath = null;
    
    protected GzipStream() {
    }
    
    public GzipStream withCompression(int v) {
    	this.compressionLevel = v;
    	return this;
    }

	@Override
	public void init(StackEntry stack, XElement el) {
		this.nameHint = stack.stringFromElement(el, "NameHint");
	}

	@Override
    public void close() throws OperatingContextException {
		//System.out.println("GZip killed");	// TODO
		
    	this.deflater = null;
    
    	super.close();
    }
    
	// make sure we don't return without first releasing the file reference content
	@Override
	public ReturnOption handle(FileSlice slice) throws OperatingContextException {
    	if (slice == FileSlice.FINAL) 
    		return this.consumer.handle(slice);
    	
		// TODO add support this.tabulator
    	
    	// we don't know what to do with a folder at this stage - gzip is for file content only
    	// folder scanning is upstream in the FileSourceStream and partners
    	if (slice.file.isFolder())
    		return ReturnOption.CONTINUE;
    	
    	// init if not set for this round of processing 
    	if (this.deflater == null) {
            this.deflater = new Deflater(this.compressionLevel, true);
            this.crc.reset();
        	this.writeHeader = true;
    	}
        
    	ByteBuf in = slice.data;
        ByteBuf out = null; 
    	
		if (in != null) {
	        byte[] inAry = in.array();
	
	        // always allow for a header (10) plus footer (8) plus extra (12)
	        // in addition to content
	        int sizeEstimate = (int) Math.ceil(in.readableBytes() * 1.001) + 30;
	        out = ApplicationHub.getBufferAllocator().heapBuffer(sizeEstimate);
	        
	        if (this.writeHeader) {
	        	this.writeHeader = false;
	            out.writeBytes(gzipHeader);
	        } 
	
	        this.crc.update(inAry, in.arrayOffset(), in.writerIndex());
	
	        this.deflater.setInput(inAry, in.arrayOffset(), in.writerIndex());
	        
	        while (!this.deflater.needsInput()) 
	            deflate(out);
		}
		else
			out = ApplicationHub.getBufferAllocator().heapBuffer(30);
		
		FileDescriptor blk = new FileDescriptor();
		FileSlice sliceout = FileSlice.allocate(blk, out, 0, false);
		
		if (StringUtil.isEmpty(this.lastpath)) {
			if (StringUtil.isNotEmpty(this.nameHint)) 
				this.lastpath = "/" +  this.nameHint;
			else if (slice.file.getPath() != null) 
				this.lastpath = "/" +  GzipUtils.getCompressedFilename(slice.file.getName());
			else
				this.lastpath = "/" + FileUtil.randomFilename("gz");
		}
		
		blk.withPath(this.lastpath);
		
		slice.file.withModificationTime(ZonedDateTime.now(ZoneId.of("UTC")));

        if (slice.isEof()) {
	        this.deflater.finish();
	        
	        while (!this.deflater.finished()) 
	            deflate(out);
	    
	        int crcValue = (int) this.crc.getValue();
	        
	        out.writeByte(crcValue);
	        out.writeByte(crcValue >>> 8);
	        out.writeByte(crcValue >>> 16);
	        out.writeByte(crcValue >>> 24);
	        
	        int uncBytes = this.deflater.getTotalIn();
	        
	        out.writeByte(uncBytes);
	        out.writeByte(uncBytes >>> 8);
	        out.writeByte(uncBytes >>> 16);
	        out.writeByte(uncBytes >>> 24);
	
	        this.deflater.end();
	        this.deflater = null;		// cause a reset for next time we use stream
	        
        	sliceout.setEof(true);
        }
        
        if (in != null)
        	in.release();
        
       	return this.consumer.handle(sliceout);
    }

    protected void deflate(ByteBuf out) {
        int numBytes = 0;
        
        do {
        	byte[] o = out.array();
        	
            numBytes = this.deflater.deflate(o, out.arrayOffset() + out.writerIndex(), out.writableBytes(), Deflater.SYNC_FLUSH);
            
            out.writerIndex(out.writerIndex() + numBytes);
        } while (numBytes > 0);
    }
    
    @Override
    public void read() throws OperatingContextException {
    	this.upstream.read();
    }
}
