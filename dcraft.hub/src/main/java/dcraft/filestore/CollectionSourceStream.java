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
package dcraft.filestore;

import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
import dcraft.hub.op.OperationOutcome;
import dcraft.stream.IStreamSource;
import dcraft.stream.IStreamUp;
import dcraft.stream.ReturnOption;
import dcraft.stream.StreamFragment;
import dcraft.stream.file.FileSlice;
import dcraft.stream.file.TransformFileStream;
import dcraft.task.IParentAwareWork;
import dcraft.xml.XElement;

public class CollectionSourceStream extends TransformFileStream implements IStreamSource {
	static public CollectionSourceStream of(FileStoreFile... src) {
		return CollectionSourceStream.of(new FileCollection().withFiles(src));
	}
	
	static public CollectionSourceStream of(IFileCollection src) {
		CollectionSourceStream fss = new CollectionSourceStream();
		fss.source = src;
		return fss;
	}

	protected IFileCollection source = null;
	
	protected CollectionSourceStream() {
	}
	
	// for use with dcScript
	@Override
	public void init(IParentAwareWork stack, XElement el) {
		// anything we need to gleam from the xml?
	}
	
	@Override
	public void close() throws OperatingContextException {
		this.source = null;
		
		super.close();
	}

	public CollectionSourceStream withFiles(FileStoreFile... src) {
		if (this.source instanceof FileCollection)
			((FileCollection) this.source).withFiles(src);

		// TODO error

		return this;
	}

	/**
	 * Someone downstream wants more data
	 */
	@Override
	public void read() throws OperatingContextException {
		if (this.handlerFlush() != ReturnOption.CONTINUE)
			return;
		
		if (this.upstream == null) {
			this.source.next(new OperationOutcome<FileStoreFile>() {
				@Override
				public void callback(FileStoreFile result) throws OperatingContextException {
					if (this.hasErrors()) {
						OperationContext.getAsTaskOrThrow().returnEmpty();
						return;
					}
					
					if (result == null) {
						CollectionSourceStream.this.consumer.handle(FileSlice.FINAL);
					}
					else if (result.isFolder()) {
						// cause a read again
						OperationContext.getAsTaskOrThrow().resume();
					}
					else {
						StreamFragment fragment = result.allocStreamSrc();

						CollectionSourceStream.this.currfile = result;
						// TODO there may be a lot of steps in the fragment but fragment needs to be flattened to work, review and change
						CollectionSourceStream.this.upstream = (IStreamUp) fragment.getLastStep();
						CollectionSourceStream.this.upstream.setDownstream(CollectionSourceStream.this);
						CollectionSourceStream.this.upstream.read();
					}
				}
			});
		}
		else {
			this.upstream.read();
		}
	}
	
	@Override
	public ReturnOption handle(FileSlice slice) throws OperatingContextException {
		if (slice == FileSlice.FINAL) {
			this.upstream.cleanup();
			this.upstream = null;
			this.currfile = null;
			
			OperationContext.getAsTaskOrThrow().resume();
			
			return ReturnOption.AWAIT;
		}
		
		// TODO if not same descriptor
		//if (this.tabulator != null)
		//	this.tabulator.accept(slice.getFile());
		
		return this.consumer.handle(slice);
	}
}
