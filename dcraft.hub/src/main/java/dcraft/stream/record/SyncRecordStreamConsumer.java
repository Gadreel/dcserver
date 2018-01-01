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
package dcraft.stream.record;

import dcraft.filestore.FileStoreFile;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
import dcraft.hub.op.OperationOutcomeStruct;
import dcraft.log.Logger;
import dcraft.stream.IServiceStreamDest;
import dcraft.stream.IStreamDest;
import dcraft.stream.IStreamDown;
import dcraft.stream.ReturnOption;
import dcraft.struct.RecordStruct;

import java.util.function.Consumer;

abstract public class SyncRecordStreamConsumer extends BaseRecordStream implements IRecordStreamDest, IServiceStreamDest<RecordStruct> {
	protected boolean started = false;
	protected Consumer<RecordStruct> tabulator = null;

    @Override
    public void start() throws OperatingContextException {
	}
	
    @Override
    public void end(OperationOutcomeStruct outcome) throws OperatingContextException {
	}
	
    abstract public void accept(RecordStruct slice) throws OperatingContextException;
	
	@Override
	public IStreamDown<RecordStruct> withTabulator(Consumer<RecordStruct> v) throws OperatingContextException {
		this.tabulator = v;
		return this;
	}
	
	/*
		 * null means done
		 */
    @Override
    public ReturnOption handle(RecordStruct slice) throws OperatingContextException {
    	if (! this.started) {
    		this.start();
    		this.started = true;
    	}
    	
    	if (slice == null) {
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
	
		if (this.tabulator != null)
			this.tabulator.accept(slice);
    	
    	this.accept(slice);

       	return ReturnOption.CONTINUE;
    }

	@Override
	public void execute() throws OperatingContextException {
		this.upstream.read();
	}
}
