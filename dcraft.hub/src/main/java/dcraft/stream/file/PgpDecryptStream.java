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

import java.security.MessageDigest;
import java.time.Instant;
import java.time.ZoneId;

import javax.crypto.Cipher;

import dcraft.hub.resource.KeyRingResource;
import dcraft.task.IParentAwareWork;
import dcraft.util.chars.CharUtil;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

import org.bouncycastle.bcpg.PacketTags;
import org.bouncycastle.openpgp.PGPPrivateKey;

import dcraft.filestore.FileDescriptor;
import dcraft.db.util.ByteUtil;
import dcraft.hub.app.ApplicationHub;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
import dcraft.stream.ReturnOption;
import dcraft.util.Memory;
import dcraft.util.pgp.KeyRingCollection;
import dcraft.util.pgp.PublicKeyEncryptedSession;
import dcraft.xml.XElement;

public class PgpDecryptStream extends TransformFileStream {
	protected enum DecryptState {
        HEADER,
        KEY_SESSION,
        DATA_PROTECT,
        DATA_PROTECT_2,
        DATA_LITERAL,
        DATA_LITERAL_2,
        DATA_LITERAL_3,
        DATA_LITERAL_4,
        DATA_LITERAL_5,
        DATA_LITERAL_6,
        MOD_CODE,
        SKIP
    }
	
	protected DecryptState dstate = DecryptState.HEADER;
	protected int packetLength = 0;
	protected boolean partial = false;
	protected ByteBuf remnant = null;
	protected Cipher cipher = null;
	protected MessageDigest md = null;
	protected boolean decryptflag = false;
	protected boolean protectedflag = false;
	protected int fnamelen = 0;
	protected KeyRingCollection keyrings = null;
	protected KeyRingResource keyresource = null;
	protected char[] password = null;

	// password we expect for this operation, keyrings may have multiple
	public PgpDecryptStream withPassword(char[] v) {
		this.password = v;
		return this;
	}
	
	public PgpDecryptStream withKeyRings(KeyRingCollection v) {
		this.keyrings = v;
		return this;
	}
	
	public PgpDecryptStream withKeyResource(KeyRingResource v) {
		this.keyresource = v;
		return this;
	}
	
    public PgpDecryptStream() {
    }

	@Override
	public void init(IParentAwareWork stack, XElement el) {
		// TODO
	}

	@Override
    public void close() throws OperatingContextException {
		this.cipher = null;
		
		ByteBuf rem = this.remnant;
		
		if (rem != null) {
			rem.release();
			
			this.remnant = null;
		}
		
		super.close();
    }
    
	// make sure we don't return without first releasing the file reference content
	@Override
	public ReturnOption handle(FileSlice slice) throws OperatingContextException {
    	boolean eof = (slice == FileSlice.FINAL);
    	
    	//	return this.downstream.handle(slice);
		
		// TODO add support this.tabulator

    	ByteBuf in = slice.getData();

    	if (in != null) {
    		if (this.decryptflag && in.isReadable()) {
    			byte[] decrypteddata = null;
    			
    			try {
	    			if (eof || slice.isEof()) {
	    				if (in.isReadable()) {
	    		            decrypteddata = this.cipher.doFinal(in.array(), in.arrayOffset(), in.readableBytes());
	    				}
	    				else {
	    					decrypteddata = this.cipher.doFinal();
	    				}
	    				
	    				this.protectedflag = false;
	    				this.decryptflag = false;
	    			}
	    			else {
	    	            decrypteddata = this.cipher.update(in.array(), in.arrayOffset(), in.readableBytes());
	    			}
    			}
    			catch (Exception x) {
		        	OperationContext.getAsTaskOrThrow().kill("decrypt failed: " + x);
		        	return ReturnOption.DONE;
    			}
    			
	            // TODO inflate if compressed
	            
				in.release();
    			
		        in = ApplicationHub.getBufferAllocator().heapBuffer(decrypteddata.length);
		        
		        in.writeBytes(decrypteddata);
    		}
		
			ByteBuf src = in;
   
			if (this.remnant != null) {
				if (this.remnant.isReadable()) {
					src = Unpooled.copiedBuffer(this.remnant, in);
					in.release();
				}
				
				this.remnant.release();
				this.remnant = null;
			}
			
    		while (src.isReadable()) {
    			boolean needMore = false;
    			
    			switch (this.dstate) {
    			case HEADER: {
    				if (src.readableBytes() < 6) {
    					needMore = true;
		    			break;
    				}
    				
    				src.markReaderIndex();
    				int bytesread = 0;
    				
    				int hdr = src.readUnsignedByte();
		            
                    bytesread += 1;
    				
    		        if ((hdr & 0x80) == 0) {
    		        	OperationContext.getAsTaskOrThrow().kill("May not be binary");
    		        	src.release();
    		        	return ReturnOption.DONE;
    		        }
		            
		            boolean newPacket = (hdr & 0x40) != 0;
		            int tag = 0;

		            if (newPacket) {
		                tag = hdr & 0x3f;

		                int l = src.readUnsignedByte();
			            
	                    bytesread += 1;

		                if (l == 0) {
		                	this.partial = true;
		                	this.packetLength = l;
		                }
		                else if (l < 192)
		                {
		                	this.packetLength = l;
		                }
		                else if (l <= 223)
		                {
		                    int b = src.readUnsignedByte();
				            
		                    bytesread += 1;

		                    this.packetLength = ((l - 192) << 8) + (b) + 192;
		                }
		                else if (l == 255)
		                {
		                    int b1 = src.readUnsignedByte();
		                    int b2 = src.readUnsignedByte();
		                    int b3 = src.readUnsignedByte();
		                    int b4 = src.readUnsignedByte();
				            
		                    bytesread += 4;
				            
		                	this.packetLength = (b1 << 24) | (b2 << 16) |  (b3 << 8)  | b4;
		                }
		                else
		                {
		                	this.partial = true;
		                	this.packetLength = 1 << (l & 0x1f);
		                }
		            }
		            else {
		                int lengthType = hdr & 0x3;

		                tag = (hdr & 0x3f) >> 2;

		                switch (lengthType) {
		                case 0: {
		                    int b1 = src.readUnsignedByte();
				            
		                    bytesread += 1;
				            
		                	this.packetLength = b1;
		                    
		                    break;
		                }
		                case 1: {
		                    int b1 = src.readUnsignedByte();
		                    int b2 = src.readUnsignedByte();
				            
		                    bytesread += 2;
				            
		                	this.packetLength = (b1 << 8) | b2;
		                    
		                    break;
		                }
		                case 2: {
		                    int b1 = src.readUnsignedByte();
		                    int b2 = src.readUnsignedByte();
		                    int b3 = src.readUnsignedByte();
		                    int b4 = src.readUnsignedByte();
				            
		                    bytesread += 4;
				            
		                	this.packetLength = (b1 << 24) | (b2 << 16) | (b3 << 8) | b4;
		                    
		                    break;
		                }
		                case 3:
		                	this.partial = true;
		                    break;
		                default:
	    		        	OperationContext.getAsTaskOrThrow().kill("unknown length type encountered");
	    		        	src.release();
	    		        	return ReturnOption.DONE;
		                }
		            }            
    		        
		            if (this.protectedflag) {
	    				src.resetReaderIndex();
	    				byte[] fordigest = new byte[bytesread];
	    				src.readBytes(fordigest);
	    				this.md.update(fordigest);
		            }
		            
		            switch (tag) {
		            case PacketTags.PUBLIC_KEY_ENC_SESSION:
		            	this.dstate = DecryptState.KEY_SESSION;
		            	break;
		            case PacketTags.SYM_ENC_INTEGRITY_PRO:
		            	this.dstate = DecryptState.DATA_PROTECT;
		            	break;
		                /*
		            case PacketTags.COMPRESSED_DATA:
		            	this.dstate = DecryptState.DATA_COMPRESSED;
		            	break;
		                */
		            case PacketTags.LITERAL_DATA:
		            	this.dstate = DecryptState.DATA_LITERAL;
		            	break;
		            case PacketTags.MOD_DETECTION_CODE:
		            	this.dstate = DecryptState.MOD_CODE;
		            	break;
		            default:
		            	this.dstate = DecryptState.SKIP;
		            	break;
		            }
		            
    				break;
    			}
    			
    			case KEY_SESSION: {
    				if (src.readableBytes() < this.packetLength) {
    					needMore = true;
		    			break;
    				}
    				
    				try {
	                	byte[] buf2 = new byte[this.packetLength];
	                	src.readBytes(buf2);
	
	                	Memory essessmem = new Memory(buf2);
	                	essessmem.setPosition(0);
	                	
	                	PublicKeyEncryptedSession esession = new PublicKeyEncryptedSession();
	                	esession.loadFrom(essessmem);
	                	
	                	//System.out.println(" - ver: " + esession.getVersion() + " alg: " +  esession.getAlgorithm()
	                	//	+ " key id: " + esession.getKeyID());
	
	                	PGPPrivateKey skey = (this.keyresource != null)
								? this.keyresource.findSecretKey(esession.getKeyID(), this.password)
								: this.keyrings.findSecretKey(esession.getKeyID(), this.password);
	                	
	                	if (skey != null) {
		                	//System.out.println("secret private key: " + skey.getKeyID());
		                	
		                	this.cipher = esession.getDataCipher(skey, true);
		                	this.md = MessageDigest.getInstance("SHA1");
	                	}
	                	
		            	this.dstate = DecryptState.HEADER;
    				}
    				catch (Exception x) {
    		        	OperationContext.getAsTaskOrThrow().kill("Error loading the secret key: " + x);
    		        	src.release();
    		        	return ReturnOption.DONE;
    				}
    				
    				break;
    			}
    			
    			case DATA_PROTECT: {
    				if (this.cipher == null) {
    		        	OperationContext.getAsTaskOrThrow().kill("Unable to find secret key, or password is bad.");
    		        	src.release();
    		        	return ReturnOption.DONE;
    				}
    				
            		int prover = src.readUnsignedByte();
                	
                	//System.out.println("protected data ver: " + prover + " len: " + this.packetLength + " with partial: " + this.partial);

                	try {
	                    byte[] decrypteddata = slice.isEof() 
	                    		? this.cipher.doFinal(src.array(), src.arrayOffset() + src.readerIndex(), src.readableBytes())
	                    		: this.cipher.update(src.array(), src.arrayOffset() + src.readerIndex(), src.readableBytes());
	                    
	                    src.release();
	                    
	        	        src = ApplicationHub.getBufferAllocator().heapBuffer(decrypteddata.length);
	        	        
	        	        src.writeBytes(decrypteddata);
        	        
	        	        if (slice.isEof()) {
		        	        MessageDigest md2 = MessageDigest.getInstance("SHA1");
		                    md2.update(decrypteddata, 0, decrypteddata.length - 20);
		                	
		                	//System.out.println("md 2 bytes: " + (decrypteddata.length - 20));
		                    
		                	byte[] dgcalc = md2.digest();
		                	//System.out.println("pre calc mod: " + HexUtil.bufferToHex(dgcalc));
	        	        }
        	        }
        	        catch (Exception x) {
        	        	// TODO System.out.println("problem with decrypt: " + x);
        	        }
        	        
        	        // src now has decrypted data, indicate that all further data should be decrypted and digested
                    this.decryptflag = true;
                    this.protectedflag = true;
                    
	            	this.dstate = DecryptState.DATA_PROTECT_2;
    			}
    			
    			case DATA_PROTECT_2: {
    				if (src.readableBytes() < this.cipher.getBlockSize() + 2) {
    					needMore = true;
		    			break;
    				}
                	
                	// check packet is complete
                    byte[] seeddata = new byte[this.cipher.getBlockSize() + 2];
                    
                    src.readBytes(seeddata);
    		        
		            if (this.protectedflag) 
	    				this.md.update(seeddata);
              
		            /* TODO
                    if ((seeddata[seeddata.length - 2] != seeddata[seeddata.length - 4]) && (seeddata[seeddata.length - 2] != 0))
                        System.out.println("data check 1 failed.");

                    if ((seeddata[seeddata.length - 1] != seeddata[seeddata.length - 3]) && (seeddata[seeddata.length - 1] != 0))
                        System.out.println("data check 2 failed.");
                    */
		            
	            	this.dstate = DecryptState.HEADER;
                    break;
    			}
		        	
    			case DATA_LITERAL:
                	//System.out.println("data: " + this.packetLength + " with partial: " + partial);
                	
                	src.markReaderIndex();
                	
                	int ftype = src.readUnsignedByte();
                	
                	this.packetLength--;
    		        
		            if (this.protectedflag) {
	    				src.resetReaderIndex();
	    				byte[] fordigest = new byte[1];
	    				src.readBytes(fordigest);
	    				this.md.update(fordigest);
		            }
                	
                	// we only process binary type
                	if (ftype != 98) {
    		        	OperationContext.getAsTaskOrThrow().kill("wrong data type!");
    		        	src.release();
    		        	return ReturnOption.DONE;
                	}
                	
                	this.dstate = DecryptState.DATA_LITERAL_2;
	        	
    			case DATA_LITERAL_2:
    				if (src.readableBytes() < 1) {
    					needMore = true;
		    			break;
    				}
                	
    				src.markReaderIndex();
    				
                	this.fnamelen = src.readUnsignedByte();
                	
                	this.packetLength--;
    		        
		            if (this.protectedflag) {
	    				src.resetReaderIndex();
	    				byte[] fordigest = new byte[1];
	    				src.readBytes(fordigest);
	    				this.md.update(fordigest);
		            }
                	
                	this.dstate = DecryptState.DATA_LITERAL_3;
                	
    			case DATA_LITERAL_3:
    				if (src.readableBytes() < this.fnamelen) {
    					needMore = true;
		    			break;
    				}
                	
    				src.markReaderIndex();
    				
                	byte[] fnbuf = new byte[this.fnamelen];
                	src.readBytes(fnbuf);
                	
                	this.packetLength -= this.fnamelen;
    		        
		            if (this.protectedflag) {
	    				src.resetReaderIndex();
	    				byte[] fordigest = new byte[this.fnamelen];
	    				src.readBytes(fordigest);
	    				this.md.update(fordigest);
		            }
                	
		            this.currfile = new FileDescriptor();
                	this.currfile.withPath("/" + CharUtil.decode(fnbuf));
                	
                	//System.out.println("got filename: " + this.currfile.getPath());
                	
                	this.dstate = DecryptState.DATA_LITERAL_4;
                	
    			case DATA_LITERAL_4:
    				if (src.readableBytes() < 4) {
    					needMore = true;
		    			break;
    				}
    				
    				src.markReaderIndex();
    				
                	// TODO this doesn't appear to work
                	long modsecs = src.readUnsignedInt();
    		        
		            if (this.protectedflag) {
	    				src.resetReaderIndex();
	    				byte[] fordigest = new byte[4];
	    				src.readBytes(fordigest);
	    				this.md.update(fordigest);
		            }
                	
                	this.packetLength -= 4;
                	
                	this.currfile.withModificationTime(Instant.ofEpochMilli(modsecs * 1000).atZone(ZoneId.of("UTC")));
                	
                	//System.out.println("got filedate: " + TimeUtil.stampFmt.format(this.currfile.getModificationAsTime()));
                	
                	this.dstate = DecryptState.DATA_LITERAL_5;
                	
    			case DATA_LITERAL_5:
		    		if (this.packetLength > 0) {
		                int readSize = (int) Math.min(this.packetLength, src.readableBytes());                
		                
		                ByteBuf obuf = src.copy(src.readerIndex(), readSize);
		                
		    			this.addSlice(obuf, 0, false);
		                
		                if (this.protectedflag)
		                	this.md.update(obuf.array(), obuf.arrayOffset(), obuf.readableBytes());
		                
		                //System.out.println("n: " + Utf8Decoder.decode(obuf));
		                
		                src.skipBytes((int) readSize);
		                
		                this.packetLength -= readSize;                
		    		}
	    			
		    		if (this.packetLength > 0) {
    					needMore = true;
		    			break;
		    		}
		    		
		    		if (!this.partial) {
		    			this.addSlice(null, 0, true);
	    				this.dstate = DecryptState.HEADER;
			            break;
		    		}
		    		
    				this.dstate = DecryptState.DATA_LITERAL_6;
		    		
    		    	/* TODO
    		        file.setModTime(this.currEntry.getModTime().getTime());
    		        
    		        file.setEof(this.remainContent == 0);
    		        
    		        */
    			case DATA_LITERAL_6:
    				// this is not really ideal, could cause an error - except we expect the mod 
    				// packet after and that will always mean at least 4 bytes...if we support
    				// non-protected data then change this
    				if (src.readableBytes() < 4) {
    					needMore = true;
		    			break;
    				}
                	
    				src.markReaderIndex();
    				int bytesread = 1;
    				
	                int l = src.readUnsignedByte();
		            
                	this.partial = false;

	                if (l < 192)
	                {
	                	this.packetLength = l;
	                }
	                else if (l <= 223)
	                {
	                    int b = src.readUnsignedByte();
	                    
	                    bytesread += 1;

	                    this.packetLength = ((l - 192) << 8) + (b) + 192;
	                }
	                else if (l == 255)
	                {
	                    int b1 = src.readUnsignedByte();
	                    int b2 = src.readUnsignedByte();
	                    int b3 = src.readUnsignedByte();
	                    int b4 = src.readUnsignedByte();
	                    
	                    bytesread += 4;
			            
	                	this.packetLength = (b1 << 24) | (b2 << 16) |  (b3 << 8)  | b4;
	                }
	                else
	                {
	                	this.partial = true;
	                	this.packetLength = 1 << (l & 0x1f);
	                }
    		        
		            if (this.protectedflag) {
	    				src.resetReaderIndex();
	    				byte[] fordigest = new byte[bytesread];
	    				src.readBytes(fordigest);
	    				this.md.update(fordigest);
		            }
	                
	                if (this.packetLength == 0)
	    				this.dstate = DecryptState.HEADER;
	                else
	    				this.dstate = DecryptState.DATA_LITERAL_5;
    				
    				break;
    			case MOD_CODE: {
    				try {
	    				if (src.readableBytes() < 20) {
	    					needMore = true;
			    			break;
	    				}
	    				
	                	byte[] dgcalc = this.md.digest();
	                	byte[] dgexpect = new byte[20];
	                    
	                    src.readBytes(dgexpect);
	                	
	                	//System.out.println("calc mod: " + HexUtil.bufferToHex(dgcalc));
	                	
	                	//System.out.println("found mod: " + HexUtil.bufferToHex(dgexpect));
	                	
	                	if (ByteUtil.compareKeys(dgcalc, dgexpect) != 0) {
	    		        	OperationContext.getAsTaskOrThrow().kill("data protected failed!");
	    		        	src.release();
	    		        	return ReturnOption.DONE;
	                	}
	    				
		            	this.dstate = DecryptState.HEADER;
	                    break;
    				}
    				catch (Exception x) {
    		        	OperationContext.getAsTaskOrThrow().kill("data protected incomplete: " + x);
    		        	src.release();
    		        	return ReturnOption.DONE;
    				}
    			}
    			case SKIP:
    	            if (!src.isReadable()) 
    	            	continue;
    	            
	    			// check if there is still padding left in the entry we were last reading from
		    		if (this.packetLength > 0) {
		                int skipSize = (int) Math.min(this.packetLength, src.readableBytes());                
		                this.packetLength -= skipSize;                
		                
		                //System.out.println("skipping content: " + skipSize);
		                
		                src.skipBytes((int) skipSize);
		    		}
	    			
	    			if (this.packetLength <= 0) 
	    				this.dstate = DecryptState.HEADER;
	    			
		            break;
    			}
    			
    			if (needMore)
    				break;
    		}
			
			// if there are any unread bytes here we need to store them and combine with the next "handle"
			if (src.isReadable()) {
				this.remnant = src;
			}
			else {
				src.release();
			}
    	}
		
    	// tell dest we got a final
    	if (slice == FileSlice.FINAL) 
    		this.outslices.add(slice);
    	
    	return this.handlerFlush();
    }
}
