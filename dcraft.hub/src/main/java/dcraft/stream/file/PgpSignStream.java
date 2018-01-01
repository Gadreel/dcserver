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

import java.io.BufferedOutputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;

import dcraft.struct.scalar.IntegerStruct;
import dcraft.struct.scalar.StringStruct;
import dcraft.util.HexUtil;
import org.bouncycastle.openpgp.PGPPrivateKey;
import org.bouncycastle.openpgp.PGPPublicKey;
import org.bouncycastle.openpgp.PGPSecretKey;
import org.bouncycastle.openpgp.PGPSecretKeyRing;
import org.bouncycastle.openpgp.PGPSignature;
import org.bouncycastle.openpgp.PGPSignatureGenerator;
import org.bouncycastle.openpgp.PGPSignatureSubpacketGenerator;
import org.bouncycastle.openpgp.operator.bc.BcPBESecretKeyDecryptorBuilder;
import org.bouncycastle.openpgp.operator.bc.BcPGPContentSignerBuilder;
import org.bouncycastle.openpgp.operator.bc.BcPGPDigestCalculatorProvider;

import dcraft.filestore.FileDescriptor;
import dcraft.hub.op.OperatingContextException;
import dcraft.log.Logger;
import dcraft.scriptold.StackEntry;
import dcraft.stream.ReturnOption;
import dcraft.util.pgp.KeyRingCollection;
import dcraft.xml.XElement;

public class PgpSignStream extends TransformFileStream {
	protected FileDescriptor current = null;
	protected PGPSignature sig = null;
	protected PGPSecretKey signkey = null;
	protected char[] passphrase = null;
	protected PGPSignatureGenerator signgen = null; 
	protected Path outputfile = null;
	protected StringStruct sigvar = null;

	@Override
	public void init(StackEntry stack, XElement el) {
	}
    
    public PgpSignStream withSignKey(PGPSecretKeyRing v) {
    	this.signkey = KeyRingCollection.findSignKey(v);
    	
    	return this;
	}
    
    public PgpSignStream withPassphrase(String v) {
    	this.passphrase = v.toCharArray();
    	return this;
	}
	
	public PgpSignStream withPassphrase(char[] v) {
		this.passphrase = v;
		return this;
	}
    
    public PgpSignStream withOutputFile(Path v) {
    	this.outputfile = v;
    	return this;
	}
	
	public PgpSignStream withSigVar(StringStruct v) {
		this.sigvar = v;
		return this;
	}
	
	public PGPSignatureGenerator getSigner() {
		if (this.signgen != null)
			return this.signgen;
		
		try {
			//Logger.info("getSigner" );
			PGPPublicKey pubkey = this.signkey.getPublicKey();
			Logger.info("public: " + pubkey);
	        PGPPrivateKey pgpPrivateKey = this.signkey.extractPrivateKey(
	        	new BcPBESecretKeyDecryptorBuilder(new BcPGPDigestCalculatorProvider()).build(this.passphrase)
	        );
			//Logger.info("private: " + pgpPrivateKey);
	
	        PGPSignatureGenerator signatureGenerator = new PGPSignatureGenerator(
	        	new BcPGPContentSignerBuilder(pubkey.getAlgorithm(), org.bouncycastle.openpgp.PGPUtil.SHA256)
	        );
			//Logger.info("gen: " + signatureGenerator);
	        
	        signatureGenerator.init(PGPSignature.BINARY_DOCUMENT, pgpPrivateKey);
	 
	        @SuppressWarnings("rawtypes")
			Iterator it = pubkey.getUserIDs();
	        
	        if (it.hasNext()) {
	            PGPSignatureSubpacketGenerator  spGen = new PGPSignatureSubpacketGenerator();
	            spGen.setSignerUserID(false, it.next().toString());
	            signatureGenerator.setHashedSubpackets(spGen.generate());
		        
		        this.signgen = signatureGenerator;
	        }
	        
			//Logger.info("gen 2: " + this.signgen);
			
			return this.signgen;
		}
		catch (Exception x) {
			Logger.error("Unable to initialize file signing: " + x);
		}
		
		return null;
    }
	
	@Override
	public ReturnOption handle(FileSlice slice) throws OperatingContextException {
		PGPSignatureGenerator signer = this.getSigner();
		
		// TODO add support this.tabulator
		
		// TODO someday detect change of fdesc and compute separate signs for each file passed through
		
		if (signer != null) {
	    	if (slice == FileSlice.FINAL) {
	    		try {
					this.sig = signer.generate();
					
					if (this.outputfile != null) {
				        try (OutputStream keyOut = new BufferedOutputStream(Files.newOutputStream(this.outputfile))) {
				        	this.sig.encode(keyOut);
				        	keyOut.flush();
				        }
					}
					
					if (this.sigvar != null) {
						this.sigvar.setValue(HexUtil.bufferToHex(this.sig.getSignature()));
					}
				} 
	    		catch (Exception x) {
					// TODO Auto-generated catch block
					x.printStackTrace();
					
					Logger.error("Unable to sign file: " + x);
				}
	    	}
	    	else if (slice.data != null) {
	    		signer.update(slice.data.array(), slice.data.arrayOffset() + slice.data.readerIndex(), slice.data.readableBytes());
	    	}
		}
		
   		return this.consumer.handle(slice);
	}
}
