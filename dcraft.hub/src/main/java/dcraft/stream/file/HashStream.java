package dcraft.stream.file;

import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import dcraft.hub.op.OperatingContextException;
import dcraft.log.Logger;
import dcraft.script.StackUtil;
import dcraft.stream.ReturnOption;
import dcraft.task.IParentAwareWork;
import dcraft.util.HexUtil;
import dcraft.xml.XElement;
import io.netty.buffer.ByteBuf;

public class HashStream extends TransformFileStream {
	static public HashStream fromAlgo(String algorithm) {
		HashStream hs = new HashStream();
		
		try {
			hs.md = MessageDigest.getInstance(algorithm);
			return hs;
		} 
		catch (NoSuchAlgorithmException x) {
			Logger.error("Hash stream, bad algorithm:" + x);
		}

		return null;
	}
	
	static public HashStream create() {
		return new HashStream();
	}
	
	protected MessageDigest md = null;

	protected HashStream() {
	}
	
	@Override
	public void init(IParentAwareWork stack, XElement el) throws OperatingContextException {
		String algo = StackUtil.stringFromElement(stack, el, "Algorithm", "SHA-256");
		
		try {
			this.md = MessageDigest.getInstance(algo);
		} 
		catch (NoSuchAlgorithmException x) {
			Logger.error("Hash stream, bad algorithm:" + x);
		}
	}
	
	public String getHash() {
		return this.getFieldAsString("Hash");
	}

	@Override
	public ReturnOption handle(FileSlice slice) throws OperatingContextException {
		ByteBuf data = slice.getData();
		
		if (data != null) {
			for (ByteBuffer bb : data.nioBuffers())
				this.md.update(bb);
		}
		
		// TODO add support this.tabulator
		
    	if (slice == FileSlice.FINAL) 
    		this.with("Hash", HexUtil.bufferToHex(this.md.digest()));
		
		return this.consumer.handle(slice);
	}
}
