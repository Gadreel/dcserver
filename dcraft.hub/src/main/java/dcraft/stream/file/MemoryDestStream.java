package dcraft.stream.file;

import dcraft.filestore.FileDescriptor;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
import dcraft.log.Logger;
import dcraft.stream.BaseStream;
import dcraft.stream.IStreamDown;
import dcraft.stream.ReturnOption;
import dcraft.util.Memory;
import io.netty.buffer.ByteBuf;

import java.nio.ByteBuffer;
import java.util.function.Consumer;

public class MemoryDestStream extends BaseStream implements IFileStreamDest {
	protected Memory result = new Memory();

	public Memory getResult() {
		return this.result;
	}

	public Memory getResultReset() {
		this.result.setPosition(0);
		return this.result;
	}

	@Override
	public ReturnOption handle(FileSlice slice) throws OperatingContextException {
		if (slice == FileSlice.FINAL) {
			// cleanup here because although we call task complete below, and task complete
			// also does cleanup, if we aer in a work chain that cleanup may not fire for a
			// while. This is the quicker way to let go of resources - but task end will also
			try {
				this.cleanup();
			}
			catch (Exception x) {
				Logger.warn("Stream cleanup did produced errors: " + x);
			}

			OperationContext.getAsTaskOrThrow().returnEmpty();
			return ReturnOption.DONE;
		}

		ByteBuf buffer = slice.getData();

		if (buffer != null) {
			for (ByteBuffer b : buffer.nioBuffers())
				this.result.write(b);

			slice.release();
		}

		return ReturnOption.CONTINUE;
	}

	@Override
	public void execute() throws OperatingContextException {
		this.upstream.read();
	}
}
