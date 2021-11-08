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
package dcraft.script.inst.file;

import dcraft.filestore.CollectionSourceStream;
import dcraft.filestore.FileStoreFile;
import dcraft.filestore.IFileCollection;
import dcraft.hub.app.ApplicationHub;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
import dcraft.mail.SmtpWork;
import dcraft.script.StackUtil;
import dcraft.script.inst.Instruction;
import dcraft.script.work.ExecuteState;
import dcraft.script.work.InstructionWork;
import dcraft.script.work.ReturnOption;
import dcraft.stream.IStream;
import dcraft.stream.StreamFragment;
import dcraft.stream.StreamWork;
import dcraft.stream.file.MemorySourceStream;
import dcraft.struct.BaseStruct;
import dcraft.struct.ListStruct;
import dcraft.struct.RecordStruct;
import dcraft.struct.Struct;
import dcraft.struct.scalar.BinaryStruct;
import dcraft.struct.scalar.StringStruct;
import dcraft.util.StringUtil;
import dcraft.util.chars.Utf8Encoder;
import dcraft.xml.XElement;

public class Stream extends Instruction {
	static public Stream tag() {
		Stream el = new Stream();
		el.setName("dcs.Stream");
		return el;
	}

	@Override
	public XElement newNode() {
		return Stream.tag();
	}

	@Override
	public ReturnOption run(InstructionWork stack) throws OperatingContextException {
		if (stack.getState() == ExecuteState.READY) {
			BaseStruct source = StackUtil.refFromSource(stack,  "Source");		// must be stream frag or binary or text
			BaseStruct dest = StackUtil.refFromSource(stack,  "Destination");		// must be stream frag

			if (! (source instanceof IStream)) {
				if (source instanceof BinaryStruct) {
					source = MemorySourceStream.fromBinary((BinaryStruct) source);
				}
				else if (source instanceof FileStoreFile) {
					source = ((FileStoreFile) source).allocStreamSrc();
				}
				else if (source instanceof IFileCollection) {
					source = CollectionSourceStream.of((IFileCollection) source);
				}
				else if (source instanceof BaseStruct) {
					source = MemorySourceStream.fromBinary(Utf8Encoder.encode(Struct.objectToString(source)));
				}
			}
			
			if (source instanceof IStream) {
				source = StreamFragment.of((IStream) source);
			}

			if (! (dest instanceof IStream)) {
				if (dest instanceof FileStoreFile) {
					dest = ((FileStoreFile) dest).allocStreamDest();
				}
			}
			
			if (dest instanceof IStream) {
				dest = StreamFragment.of((IStream) dest);
			}
			
			if (source instanceof StreamFragment && dest instanceof StreamFragment) {
				stack.setState(ExecuteState.RESUME);
				
				OperationContext.getAsTaskOrThrow().resumeWith(StreamWork.of((StreamFragment) source, (StreamFragment) dest));
				
				return ReturnOption.AWAIT;
			}
		}

		return ReturnOption.CONTINUE;
	}
}
