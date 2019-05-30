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

import dcraft.filestore.FileDescriptor;
import dcraft.hub.op.OperatingContextException;
import dcraft.stream.ReturnOption;
import dcraft.task.IParentAwareWork;
import dcraft.xml.XElement;

import java.util.function.Consumer;

public class FileObserverStream extends TransformFileStream {
	// allow just one file
	static public FileObserverStream of(Consumer<FileDescriptor> observer) {
		return new FileObserverStream().withObserver(observer);
	}

	protected FileDescriptor jfile = null;
	protected Consumer<FileDescriptor> observer = null;

	public FileObserverStream withObserver(Consumer<FileDescriptor> v) {
		this.observer = v;
		return this;
	}

	public FileObserverStream() {
    }

	@Override
	public void init(IParentAwareWork stack, XElement el) {
	}
    
	// make sure we don't return without first releasing the file reference content
	@Override
	public ReturnOption handle(FileSlice slice) throws OperatingContextException {
    	if (slice == FileSlice.FINAL) 
    		return this.consumer.handle(slice);

		if (this.jfile != slice.file) {
			this.jfile = slice.file;
			this.observer.accept(this.jfile);
		}
		
		return this.consumer.handle(slice);
    }

    // should we close after got the one file?
}
