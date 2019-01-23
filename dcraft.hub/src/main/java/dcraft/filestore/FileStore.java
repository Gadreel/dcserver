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
import dcraft.hub.op.OperationOutcomeEmpty;
import dcraft.script.work.ExecuteState;
import dcraft.script.work.ReturnOption;
import dcraft.script.work.StackWork;
import dcraft.struct.RecordStruct;
import dcraft.task.IParentAwareWork;
import dcraft.xml.XElement;

import java.util.List;

abstract public class FileStore extends RecordStruct {
	abstract public boolean close(OperationOutcomeEmpty callback) throws OperatingContextException;
	abstract public void connect(RecordStruct params, OperationOutcomeEmpty callback) throws OperatingContextException;
	abstract public void removeFolder(CommonPath path, OperationOutcomeEmpty callback) throws OperatingContextException;
	abstract public void queryFeatures(OperationOutcome<RecordStruct> callback) throws OperatingContextException;
	abstract public void customCommand(RecordStruct params, OperationOutcome<RecordStruct> callback) throws OperatingContextException;
	abstract public IFileCollection scanner(CommonPath path) throws OperatingContextException;
	abstract public FileStoreFile rootFolder() throws OperatingContextException;
	abstract public FileStoreFile fileReference(CommonPath path) throws OperatingContextException;
	abstract public FileStoreFile fileReference(CommonPath path, boolean isFolder) throws OperatingContextException;
	abstract public void getFolderListing(CommonPath path, OperationOutcome<List<FileStoreFile>> callback) throws OperatingContextException;
	abstract public void getFileDetail(CommonPath path, OperationOutcome<FileStoreFile> callback) throws OperatingContextException;
	abstract public void addFolder(CommonPath path, OperationOutcome<FileStoreFile> callback) throws OperatingContextException;
	
	@Override
	public ReturnOption operation(StackWork stack, XElement code) throws OperatingContextException {
		if ("Connect".equals(code.getName())) {
			this.connect(null, new OperationOutcomeEmpty() {
				@Override
				public void callback() throws OperatingContextException {
					stack.setState(ExecuteState.DONE);
					
					OperationContext.getAsTaskOrThrow().resume();
				}
			});
			
			return ReturnOption.AWAIT;
		}
		
		if ("Close".equals(code.getName())) {
			this.close(new OperationOutcomeEmpty() {
				@Override
				public void callback() throws OperatingContextException {
					stack.setState(ExecuteState.DONE);
					
					OperationContext.getAsTaskOrThrow().resume();
				}
			});

			return ReturnOption.AWAIT;
		}

		// TODO expand
		
		return super.operation(stack, code);
	}
}
