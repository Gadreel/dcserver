package dcraft.util.io;

import dcraft.hub.app.ApplicationHub;
import dcraft.util.chars.Utf8Encoder;
import io.netty.buffer.ByteBuf;

public class ByteBufWriter implements AutoCloseable {
	static public ByteBufWriter createLargeHeap() {
		ByteBufWriter bw = new ByteBufWriter();
		bw.buffer = ApplicationHub.getBufferAllocator().heapBuffer(32 * 1024, 4 * 1024 * 1024);
		return bw;
	}
	
	static public ByteBufWriter createSmallHeap() {
		ByteBufWriter bw = new ByteBufWriter();
		bw.buffer = ApplicationHub.getBufferAllocator().heapBuffer(1024, 32 * 1024);
		return bw;
	}
	
	static public ByteBufWriter createLargeDirect() {
		ByteBufWriter bw = new ByteBufWriter();
		bw.buffer = ApplicationHub.getBufferAllocator().directBuffer(32 * 1024, 4 * 1024 * 1024);
		return bw;
	}
	
	static public ByteBufWriter createSmallDirect() {
		ByteBufWriter bw = new ByteBufWriter();
		bw.buffer = ApplicationHub.getBufferAllocator().directBuffer(1024, 32 * 1024);
		return bw;
	}
	
	protected ByteBuf buffer = null;
	
	protected ByteBufWriter() {		
	}
	
	public void write(CharSequence v) {
		Utf8Encoder.encode(v, this.buffer);
	}
	
	public void writeLine(String v) {
		Utf8Encoder.encode(v, this.buffer);
		this.buffer.writeBytes(Utf8Encoder.encode('\n')); 
	}
	
	public void writeLine() {
		this.buffer.writeBytes(Utf8Encoder.encode('\n')); 
	}
	
	public void writeChar(int v) {
		this.buffer.writeBytes(Utf8Encoder.encode(v)); 
	}
	
	public void write(ByteBuf v) {
		this.buffer.writeBytes(v);
		v.release();
	}

	public int readableBytes() {
		return this.buffer.readableBytes();
	}

	public ByteBuf getByteBuf() {
		return this.buffer;
	}

	@Override
	public void close() throws Exception {
		this.buffer.release();
		this.buffer = null;
	}
}
