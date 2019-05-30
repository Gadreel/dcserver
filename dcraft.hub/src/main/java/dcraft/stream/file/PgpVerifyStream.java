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

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import dcraft.hub.resource.KeyRingResource;
import dcraft.struct.scalar.StringStruct;
import dcraft.task.IParentAwareWork;
import dcraft.util.HexUtil;
import org.bouncycastle.openpgp.*;
import org.bouncycastle.openpgp.jcajce.JcaPGPObjectFactory;
import org.bouncycastle.openpgp.operator.jcajce.JcaPGPContentVerifierBuilderProvider;

import dcraft.filestore.FileDescriptor;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
import dcraft.log.Logger;
import dcraft.stream.ReturnOption;
import dcraft.util.pgp.KeyRingCollection;
import dcraft.xml.XElement;

// sigs are generated one per file that passes through this stream part
public class PgpVerifyStream extends TransformFileStream {
	protected FileDescriptor current = null;
	protected PGPSignature sig = null;
	protected StringStruct sigvar = null;
	protected Path sigfile = null;
	protected Path sigfolder = null;
	protected String currfile = null;
	protected boolean replaceExt = false;
	protected boolean addExt = false;
	protected KeyRingCollection rings = null;
	protected KeyRingResource keyresource = null;

	@Override
	public void init(IParentAwareWork stack, XElement el) {
	}
    
    public PgpVerifyStream withKeyRings(KeyRingCollection v) {
    	this.rings = v;
    	return this;
	}
	
	public PgpVerifyStream withKeyResource(KeyRingResource v) {
		this.keyresource = v;
		return this;
	}
    
    // TODO enhance so that we can use FileStoreFile as well, or instead of
    public PgpVerifyStream withSignatureFile(Path v) {
    	this.sigfile = v;
    	return this;
	}
    
    // TODO enhance so that we can use FileStoreDriver as well (with relative paths), or instead of
    // make this the root folder of file source
    public PgpVerifyStream withSignatureFolder(Path v) {
    	this.sigfolder = v;
    	return this;
	}
	
	public PgpVerifyStream withSigVar(StringStruct v) {
		this.sigvar = v;
		return this;
	}

	public PgpVerifyStream withReplaceExt() {
		this.replaceExt = true;
		return this;
	}

	public PgpVerifyStream withAddExt() {
		this.addExt = true;
		return this;
	}
    
	// TODO once we support FileStores we'll want this to become async, use callback
	public PGPSignature getSignature(FileDescriptor file) {
		if (this.sig != null)
			return this.sig;
		
		if ((this.sigfile == null) && (file != null) && (this.sigfolder != null)) {
			String fpath = file.getPath();
			
			if (this.replaceExt) {
				fpath = fpath.substring(1, fpath.lastIndexOf('.')) + ".sig";
			}
			else {
				fpath += fpath.substring(1) + ".sig";
			}
			
			this.sigfile = this.sigfolder.resolve(fpath);
		}
		
		if (this.sigfile == null)
			return null;
		
		try (InputStream in1 = Files.newInputStream(this.sigfile)) {
			InputStream in = PGPUtil.getDecoderStream(in1);

	        JcaPGPObjectFactory pgpFact = new JcaPGPObjectFactory(in);
	    
	        Object po = pgpFact.nextObject();
	        
	        while ((po != null) && (this.sig == null)) {
	        	if (po instanceof PGPSignatureList) {
			        PGPSignatureList psl = (PGPSignatureList) po;
			        
			        for (int si = 0; si < psl.size(); si++) {
				        PGPSignature sio = psl.get(si);
				        
				        PGPPublicKey pk = (this.keyresource != null)
								? this.keyresource.findPublicKey(sio.getKeyID())
								: this.rings.findPublicKey(sio.getKeyID());
				        
				        if (pk != null) {
				        	sio.init(new JcaPGPContentVerifierBuilderProvider().setProvider("BC"), pk);
					        this.sig = sio;
					        break;
				        }
			        }
	        	}
	        	
		        po = pgpFact.nextObject();
	        }
		}
		catch (Exception x) {
        	Logger.error("Failed to read signature file");
		}
		
		return this.sig;
    }
	
	@Override
	public ReturnOption handle(FileSlice slice) throws OperatingContextException {
		// find the signature for the current file 
		PGPSignature sig = this.getSignature(slice.file);
		
		if ((sig == null) && (slice != FileSlice.FINAL)) {
			OperationContext.getAsTaskOrThrow().kill("Error loading PGP signature");
			return ReturnOption.DONE;
		}
		
		// TODO add support this.tabulator

		// if any data is present apply it to the current signature
    	if ((slice.data != null) && (slice.data.isReadable()))
    		sig.update(slice.data.array(), slice.data.arrayOffset() + slice.data.readerIndex(), slice.data.readableBytes());
		
    	if ((sig != null) && (slice.isEof() || (slice == FileSlice.FINAL))) {
    		try {
    	        if (sig.verify())
    	        	Logger.info("Passed signature check");
    	        else
    	        	Logger.error("Failed signature check");
			
				if (this.sigvar != null)
					this.sigvar.setValue(HexUtil.bufferToHex(sig.getSignature()));
    	        
    	        // force the next file, if there is one, to use a new signature
    	        this.sig = null;
    	        this.sigfile = null;
			} 
    		catch (PGPException x) {
				OperationContext.getAsTaskOrThrow().kill("Error calculating PGP signature: " + x);
				return ReturnOption.DONE;
			}
    	}
    	
   		return this.consumer.handle(slice);
	}
}
