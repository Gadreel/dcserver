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
import dcraft.log.Logger;
import dcraft.script.StackUtil;
import dcraft.script.work.ExecuteState;
import dcraft.script.work.ReturnOption;
import dcraft.script.work.StackWork;
import dcraft.struct.BaseStruct;
import dcraft.struct.ListStruct;
import dcraft.struct.RecordStruct;
import dcraft.struct.scalar.BooleanStruct;
import dcraft.task.IParentAwareWork;
import dcraft.util.StringUtil;
import dcraft.xml.XElement;

import java.util.List;
import java.util.Map;

abstract public class FileStore extends RecordStruct {
	abstract public boolean close(OperationOutcomeEmpty callback) throws OperatingContextException;
	abstract public void connect(RecordStruct params, OperationOutcomeEmpty callback) throws OperatingContextException;
	abstract public void removeFile(CommonPath path, OperationOutcomeEmpty callback) throws OperatingContextException;
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
			RecordStruct params = RecordStruct.record();

			for (String attr : code.getAttributes().keySet()) {
				BaseStruct val = StackUtil.refFromElement(stack, code, attr, true);
				params.with(attr, val);
			}

			this.connect(params, new OperationOutcomeEmpty() {
				@Override
				public void callback() throws OperatingContextException {
					stack.setState(ExecuteState.DONE);
					
					OperationContext.getAsTaskOrThrow().resume();
				}
			});
			
			return ReturnOption.AWAIT;
		}

		if ("FolderListing".equals(code.getName())) {
			BaseStruct spath = StackUtil.refFromElement(stack, code, "Path", true);

			if (spath == null) {
				Logger.error("Missing path");
				return ReturnOption.CONTINUE;
			}

			String handle = StackUtil.stringFromElement(stack, code, "Handle");

			this.getFolderListing(CommonPath.from(spath.toString()), new OperationOutcome<List<FileStoreFile>>() {
				@Override
				public void callback(List<FileStoreFile> result) throws OperatingContextException {
					stack.setState(ExecuteState.DONE);

					if (handle != null) {
						StackUtil.addVariable(stack, handle, ListStruct.list(result));
					}

					OperationContext.getAsTaskOrThrow().resume();
				}
			});

			return ReturnOption.AWAIT;
		}

		if ("CreateFolder".equals(code.getName())) {
			BaseStruct spath = StackUtil.refFromElement(stack, code, "Path", true);

			if (spath == null) {
				Logger.error("Missing path");
				return ReturnOption.CONTINUE;
			}

			String handle = StackUtil.stringFromElement(stack, code, "Handle");

			this.addFolder(CommonPath.from(spath.toString()), new OperationOutcome<>() {
				@Override
				public void callback(FileStoreFile result) throws OperatingContextException {
					stack.setState(ExecuteState.DONE);

					if (handle != null) {
						StackUtil.addVariable(stack, handle, result);
					}

					OperationContext.getAsTaskOrThrow().resume();
				}
			});

			return ReturnOption.AWAIT;
		}

		if ("GetFileDetails".equals(code.getName())) {
			BaseStruct spath = StackUtil.refFromElement(stack, code, "Path", true);

			if (spath == null) {
				Logger.error("Missing path");
				return ReturnOption.CONTINUE;
			}

			String handle = StackUtil.stringFromElement(stack, code, "Handle");

			this.getFileDetail(CommonPath.from(spath.toString()), new OperationOutcome<>() {
				@Override
				public void callback(FileStoreFile result) throws OperatingContextException {
					stack.setState(ExecuteState.DONE);

					if (handle != null) {
						StackUtil.addVariable(stack, handle, result);
					}

					OperationContext.getAsTaskOrThrow().resume();
				}
			});

			return ReturnOption.AWAIT;
		}

		if ("Delete".equals(code.getName())) {
			BaseStruct spath = StackUtil.refFromElement(stack, code, "Path", true);

			if (spath == null) {
				Logger.error("Missing path");
				return ReturnOption.CONTINUE;
			}

			this.removeFile(CommonPath.from(spath.toString()), new OperationOutcomeEmpty() {
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
