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

import dcraft.hub.app.ApplicationHub;
import dcraft.task.IParentAwareWork;
import dcraft.util.StringUtil;
import dcraft.util.chars.CharUtil;
import io.netty.buffer.ByteBuf;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarConstants;
import org.apache.commons.compress.archivers.zip.ZipEncoding;
import org.apache.commons.compress.archivers.zip.ZipEncodingHelper;
import org.apache.commons.compress.utils.ArchiveUtils;

import dcraft.filestore.FileDescriptor;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
import dcraft.stream.ReturnOption;
import dcraft.xml.XElement;

import java.time.ZoneId;

public class UntarStream extends TransformFileStream {
	protected enum UntarState {
        RECORD,
        XTRAS,
        PREP,
        CONTENT,
        SKIP,
		LONGNAMECONTENT
    }
	
    protected byte[] header_buffer = new byte[TarConstants.DEFAULT_RCDSIZE];
    protected int partialLength = 0;
    
    protected TarArchiveEntry currEntry = null;
    protected ByteBuf longnamebuff = null;
    protected CharSequence longname = null;
    protected ZipEncoding encoding = null;
    protected long remainContent = 0;
    protected long remainSkip = 0;

    protected UntarState tstate = UntarState.RECORD;
    
    public UntarStream() {
        this.encoding  = ZipEncodingHelper.getZipEncoding("UTF-8");
    }

	@Override
	public void init(IParentAwareWork stack, XElement el) {
	}
	
	@Override
	public void close() throws OperatingContextException {
		ByteBuf namebuff = this.longnamebuff;
		
		if (namebuff != null) {
			this.longnamebuff = null;
			
			try {
				namebuff.release();
			}
			catch (Exception x) {
				// ignore
			}
		}
		
		super.close();
	}
	
	// make sure we don't return without first releasing the file reference content
	@Override
	public ReturnOption handle(FileSlice slice) throws OperatingContextException {
    	if (slice == FileSlice.FINAL) 
    		return this.consumer.handle(slice);
    	
    	// TODO add support this.tabulator

    	ByteBuf in = slice.getData();

    	if (in != null) {
    		while (in.isReadable()) {

    			switch (this.tstate) {
    			case RECORD:

		    		// starting a new record
		    		if (in.readableBytes() < TarConstants.DEFAULT_RCDSIZE - this.partialLength) {
		    			int offset = this.partialLength;
		    			
		    			this.partialLength += in.readableBytes();
		    			
		    			in.readBytes(this.header_buffer, offset, in.readableBytes());

		    			continue;
		    		}

	    			in.readBytes(this.header_buffer, this.partialLength, TarConstants.DEFAULT_RCDSIZE - this.partialLength);
		    		
		    		this.partialLength = 0;
				
					//System.out.println("buffer 1: " + HexUtil.bufferToHex(this.header_buffer));

		            boolean hasHitEOF = this.isEOFRecord(this.header_buffer);

		            // if we hit this twice in a row we are at the end - however, source will send FINAL anyway so we don't really care
		            if (hasHitEOF) {
		                this.setEntry(null); 
		                continue;
		            }
		    		
		            try {
						TarArchiveEntry te = new TarArchiveEntry(this.header_buffer, this.encoding);
						
						if (te.isGNULongNameEntry()) {
							this.setEntry(null);
							
							long entrySize = te.getSize();
							this.remainContent = entrySize;
							
							// max path length for dcServer
							if (this.remainContent > 4048) {
								OperationContext.getAsTaskOrThrow().kill("Max path length exceeded");
								in.release();
								return ReturnOption.DONE;
							}
				
							if (entrySize % this.header_buffer.length > 0) {
								long numRecords = (entrySize / this.header_buffer.length) + 1;
					
								this.remainSkip = (numRecords * this.header_buffer.length) - entrySize;
							}
							else {
								this.remainSkip = 0;
							}
							
							this.longnamebuff = ApplicationHub.getBufferAllocator().heapBuffer(4048);		// max length for path
							
							this.tstate = UntarState.LONGNAMECONTENT;
							continue;
						}
						/*
						else if (te.isGNULongLinkEntry()) {
							this.tstate = UntarState.LONGLINKCONTENT;
							continue;
						}
						*/
						else {
							this.setEntry(te);
						}
		            }
		            catch (Exception x) {
		            	OperationContext.getAsTaskOrThrow().kill("Error detected parsing the header: " + x);
		                in.release();
		                return ReturnOption.DONE;
		            }
		            
		            this.tstate = UntarState.XTRAS;
    			case XTRAS:
		            if (!in.isReadable())
		            	continue;
			
			        if (this.currEntry.isPaxHeader()) { 
			        	// TODO Process Pax headers
			    		/* 
			            paxHeaders();
			            */
			        	
			        	OperationContext.getAsTaskOrThrow().kill("pax currently not supported");
		                in.release();
			        	return ReturnOption.DONE;
			        }
			
			        if (this.currEntry.isGNUSparse()) { 
			        	// TODO Process sparse files
			    		/* 
			            readGNUSparse();
			            */
			        	
			        	OperationContext.getAsTaskOrThrow().kill("sparse currently not supported");
		                in.release();
			        	return ReturnOption.DONE;
			        }
    				
		            this.tstate = UntarState.PREP;
    			case PREP:
		            if (! in.isReadable())
		            	continue;
		            
			        // TODO remove
		            //System.out.println("name: " + this.currEntry.getName());
		            //System.out.println("size: " + this.currEntry.getSize());
		            //System.out.println("modified: " + this.currEntry.getModTime());
		            
		            // If the size of the next element in the archive has changed
		            // due to a new size being reported in the posix header
		            // information, we update entrySize here so that it contains
		            // the correct value.
		            long entrySize = this.currEntry.getSize();
		            this.remainContent = entrySize;
		            
		            this.currfile.withSize(entrySize);    // not always there in first creating FD
					
					if (StringUtil.isNotEmpty(this.longname)) {
						this.currfile.withPath("/" + this.longname.toString());
						this.longname = null;
					}
		            
		            if (entrySize % this.header_buffer.length > 0) {
						long numRecords = (entrySize / this.header_buffer.length) + 1;

						this.remainSkip = (numRecords * this.header_buffer.length) - entrySize;
					}
					else {
		            	this.remainSkip = 0;
					}

		            this.tstate = UntarState.CONTENT;
    			case CONTENT:
		            if (! in.isReadable())
		            	continue;

	    			// check if there is still content left in the entry we were last reading from
	    			if (this.remainContent > 0) {
	    	            int readSize = (int) Math.min(this.remainContent, in.readableBytes());
	    	            this.remainContent -= readSize;
	    	            
	    	            //System.out.println("reading content: " + readSize);

	    	            ByteBuf out = in.copy(in.readerIndex(), readSize);

						in.skipBytes(readSize);

			            this.addSlice(out, 0, this.remainContent == 0);
	    			}
	    			
	    			if (this.remainContent > 0) 
	    				continue;
	    			
		            this.setEntry(null);
		            
		            this.tstate = UntarState.SKIP;
    			case SKIP:
    	            if (!in.isReadable())
    	            	continue;

	    			// check if there is still padding left in the entry we were last reading from
		    		if (this.remainSkip > 0) {
		                int skipSize = (int) Math.min(this.remainSkip, in.readableBytes());                
		                this.remainSkip -= skipSize;                
		                
		                //System.out.println("skipping content: " + skipSize);
		                
		                in.skipBytes((int) skipSize);
		    		}
	    			
	    			if (this.remainSkip > 0) 
	    				continue;
		    		
		            this.tstate = UntarState.RECORD;
		            continue;
				case LONGNAMECONTENT:
					if (! in.isReadable())
						continue;
				
					// check if there is still content left in the entry we were last reading from
					if (this.remainContent > 0) {
						int readSize = (int) Math.min(this.remainContent, in.readableBytes());
						this.remainContent -= readSize;
					
						//System.out.println("reading long name: " + readSize);
					
						ByteBuf out = in.copy(in.readerIndex(), readSize);
					
						in.skipBytes(readSize);
						
						this.longnamebuff.writeBytes(out);
					
						out.release();
					}
				
					if (this.remainContent > 0)
						continue;
					
					this.longname = CharUtil.decode(this.longnamebuff);
					
					//System.out.println("name: " + longname);
					
					this.longnamebuff.release();
				
					this.tstate = UntarState.SKIP;
    			}
    		}

    		in.release();
    	}

		// write all messages in the queue
    	return this.handlerFlush();
    }

	public void setEntry(TarArchiveEntry v) {
		if (v == null) {
			this.currEntry = null;
			this.currfile = null;
		}
		else {
			// create the output message
			FileDescriptor blk = new FileDescriptor();
			
	        blk.withPath("/" + v.getName());
	        blk.withSize(v.getRealSize());
	        blk.withModificationTime(v.getModTime().toInstant().atZone(ZoneId.of("UTC")));
	        
	        this.currfile = blk;
			this.currEntry = v;
		}
	}
    
    protected boolean isEOFRecord(byte[] record) {
        return record == null || ArchiveUtils.isArrayZero(record, TarConstants.DEFAULT_RCDSIZE);
    }
}
