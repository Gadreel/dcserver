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

import dcraft.script.StackUtil;
import dcraft.task.IParentAwareWork;
import dcraft.util.StringUtil;
import io.netty.buffer.ByteBuf;

import java.io.IOException;
import java.nio.file.Paths;

import org.bouncycastle.openpgp.PGPException;
import org.bouncycastle.openpgp.PGPPublicKeyRing;

import dcraft.filestore.FileDescriptor;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
import dcraft.log.Logger;
import dcraft.stream.ReturnOption;
import dcraft.util.FileUtil;
import dcraft.util.pgp.EncryptedFileStream;
import dcraft.xml.XElement;

public class PgpEncryptStream extends TransformFileStream {
	protected EncryptedFileStream pgp = new EncryptedFileStream();
	protected boolean needInit = true;
	protected FileDescriptor efile = null;
	protected String nameHint = null;
	protected boolean useTGZGext = false;
	
    public PgpEncryptStream() {
    }
    
    public PgpEncryptStream withPgpKeyring(PGPPublicKeyRing ring) {
    	this.pgp.addPublicKey(ring);
    
    	return this;
    }
    
    public PgpEncryptStream withAlgorithm(int v) {
		this.pgp.setAlgorithm(v); 	    
    	return this;
	}
	
	public PgpEncryptStream withNameHint(String v) {
		this.nameHint = v;
		return this;
	}
	
	public PgpEncryptStream withTgzgFormat(boolean v) {
		this.useTGZGext = v;
		return this;
	}
 
	@Override
	public void init(IParentAwareWork stack, XElement el) throws OperatingContextException {
		String keyPath = StackUtil.stringFromElement(stack, el, "Keyring");
		
		try {
			this.pgp.loadPublicKey(Paths.get(keyPath));
		} 
		catch (IOException x) {
			Logger.error("Unabled to read keyfile: " + x);
		} 
		catch (PGPException x) {
			Logger.error("Unabled to load keyfile: " + x);
		}
		
		this.nameHint = StackUtil.stringFromElement(stack, el, "NameHint");
	}
    
	@Override
    public void close() throws OperatingContextException {
		try {
			this.pgp.close();
		} 
		catch (PGPException x) {
			// it should already be closed, unless we got here by a task kill/cancel
			Logger.warn("Error closing PGP stream: " + x);
		}
    
    	super.close();
    }

	// make sure we don't return without first releasing the file reference content
	@Override
	public ReturnOption handle(FileSlice slice) throws OperatingContextException {
    	if (slice == FileSlice.FINAL) 
    		return this.consumer.handle(slice);
    	
    	if (this.needInit) {
    		this.pgp.setFileName(slice.file.getName());
    		
    		try {
    			this.pgp.init();
    		}
    		catch (Exception x) {
    			OperationContext.getAsTaskOrThrow().kill("PGP init failed: " + x);
    			return ReturnOption.DONE;
    		}
    		
    		this.initializeFileValues(slice.file);
    		
    		this.needInit = false;
    	}
    	
		// TODO add support this.tabulator
    	
    	// inflate the payload into 1 or more outgoing buffers set in a queue
    	ByteBuf in = slice.data;
    	
		if (in != null) {
			this.pgp.writeData(in);
			
        	in.release();
			
			if (OperationContext.getAsTaskOrThrow().isComplete())
				return ReturnOption.DONE;
		}
		
		// write all buffers in the queue
        ByteBuf buf = this.pgp.nextReadyBuffer();
        
        while (buf != null) {
        	ReturnOption ret = this.nextMessage(buf);
			
			if (ret != ReturnOption.CONTINUE)
				return ret;
        	
        	buf = this.pgp.nextReadyBuffer();
        }
		
        if (slice.isEof()) {
        	try {
				this.pgp.close();
			} 
        	catch (PGPException x) {
        		OperationContext.getAsTaskOrThrow().kill("PGP close failed: " + x);
				return ReturnOption.DONE;
			}
        	
    		// write all buffers in the queue
            buf = this.pgp.nextReadyBuffer();
            
            while (buf != null) {
            	ReturnOption ret = this.nextMessage(buf);
    			
    			if (ret != ReturnOption.CONTINUE)
    				return ret;
            	
            	buf = this.pgp.nextReadyBuffer();
            }
            
            ReturnOption ret = this.lastMessage();
			
			if (ret != ReturnOption.CONTINUE)
				return ret;
        }
		
		// otherwise we need more data
		return ReturnOption.CONTINUE;
    }
    
    public ReturnOption nextMessage(ByteBuf out) throws OperatingContextException {
        return this.consumer.handle(FileSlice.allocate(this.efile, out, 0, false));
    }
    
    public ReturnOption lastMessage() throws OperatingContextException {
        return this.consumer.handle(FileSlice.allocate(this.efile, null, 0, true));
    }
    
    public void initializeFileValues(FileDescriptor src) {
    	this.efile = new FileDescriptor();
	
		if (StringUtil.isNotEmpty(this.nameHint))
			this.efile.withPath("/" + this.nameHint + ".gpg");
		else if (StringUtil.isNotEmpty(src.getPath())) {
			String spath = src.getPath();
			
			if (spath.endsWith(".tgz") && this.useTGZGext)
				this.efile.withPath(src.getPath() + "g");
			else
				this.efile.withPath(src.getPath() + ".gpg");
		}
		else
			this.efile.withPath("/" + FileUtil.randomFilename("bin") + ".gpg");
		
		this.efile.withModificationTime(src.getModificationAsTime());
		// TODO this.efile.setPermissions(src.getPermissions());
    }
    
    @Override
    public void read() throws OperatingContextException {
    	// TODO can we use the common buff routine?
		// write all buffers in the queue
        ByteBuf buf = this.pgp.nextReadyBuffer();
        
        while (buf != null) {
        	ReturnOption ret = this.nextMessage(buf);
			
			if (ret != ReturnOption.CONTINUE)
				return;
        	
        	buf = this.pgp.nextReadyBuffer();
        }
		
		// if we reached done and we wrote all the buffers, then send the EOF marker if not already
		if (this.pgp.isClosed()) {
			ReturnOption ret = this.lastMessage();
			
			if (ret != ReturnOption.CONTINUE)
				return;
		}
		
    	this.upstream.read();
    }	
}
