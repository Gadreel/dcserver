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

import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
import dcraft.log.Logger;
import dcraft.stream.IStreamDest;
import dcraft.stream.ReturnOption;
import dcraft.task.IParentAwareWork;
import dcraft.xml.XElement;

import java.util.function.Consumer;

public class NullDest extends BaseFileStream implements IStreamDest<FileSlice>, IFileStreamConsumer {
    protected int files = 0;
    protected long bytes = 0;
 
	// make sure we don't return without first releasing the file reference content
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
			
    		OperationContext.getAsTaskOrThrow().setAmountCompleted(100);
    		Logger.info("Null got " + this.bytes + " bytes and " + this.files + " files/folders.");
    		OperationContext.getAsTaskOrThrow().returnEmpty();
           	return ReturnOption.DONE;
    	}
       	
    	if (slice.isEof()) {
    		this.files++;
        	
    		//System.out.println("--- " + slice.file.getPath() + "     " + slice.file.getSize()
    		//		+ "     " + (slice.file.isFolder() ? "FOLDER" : "FILE"));
    	}
    	
    	if (slice.data != null) {
    		this.bytes += slice.data.readableBytes();
    		slice.release();
    	}
    	
       	return ReturnOption.CONTINUE;
    }

	@Override
	public void execute() throws OperatingContextException {
		this.upstream.read();
	}

	@Override
	public void init(IParentAwareWork stack, XElement el) {
		// TODO Auto-generated method stub
		
	}
}
