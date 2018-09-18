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
package dcraft.filestore.mem;

import dcraft.filestore.CommonPath;
import dcraft.filestore.FileStore;
import dcraft.filestore.FileStoreFile;
import dcraft.filestore.IFileCollection;
import dcraft.filestore.local.LocalStoreFile;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationMarker;
import dcraft.hub.op.OperationOutcome;
import dcraft.hub.op.OperationOutcomeEmpty;
import dcraft.log.Logger;
import dcraft.schema.SchemaHub;
import dcraft.script.StackUtil;
import dcraft.script.work.ReturnOption;
import dcraft.script.work.StackWork;
import dcraft.struct.RecordStruct;
import dcraft.task.IParentAwareWork;
import dcraft.util.FileUtil;
import dcraft.util.StringUtil;
import dcraft.util.io.IFileWatcher;
import dcraft.xml.XElement;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.WatchEvent;
import java.util.ArrayList;
import java.util.List;

public class MemoryStore extends FileStore {
	static public MemoryStore store() {
		MemoryStore driver = new MemoryStore();
		driver.with("RootFolder", "/");
		driver.connect(null, null);
		return driver;
	}

	// TODO correct
	public MemoryStore() {
		this.withType(SchemaHub.getType("dcLocalStore"));
	}

	@Override
	public MemoryStore deepCopy() {
		MemoryStore cp = new MemoryStore();
		this.doCopy(cp);
		return cp;
	}

	@Override
	public void connect(RecordStruct params, OperationOutcomeEmpty callback) {
		if (callback != null)
			callback.returnResult();
	}

	@Override
	public boolean close(OperationOutcomeEmpty callback) {
		return false;
	}

	@Override
	public ReturnOption operation(StackWork stack, XElement codeEl) throws OperatingContextException {
		// connect and close in super

		return super.operation(stack, codeEl);
	}

	@Override
	public void getFileDetail(CommonPath path, OperationOutcome<FileStoreFile> callback) {
		callback.returnValue(null);
	}

	@Override
	public void addFolder(CommonPath path, OperationOutcome<FileStoreFile> callback) {
		callback.returnValue(null);
	}
	
	@Override
	public void removeFolder(CommonPath path, OperationOutcomeEmpty callback) {
		callback.returnEmpty();
	}

	@Override
	public void queryFeatures(OperationOutcome<RecordStruct> callback) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void customCommand(RecordStruct params, OperationOutcome<RecordStruct> callback) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public IFileCollection scanner(CommonPath path) {
		return null;
	}

	@Override
	public FileStoreFile rootFolder() {
		return null;
	}
	
	@Override
	public FileStoreFile fileReference(CommonPath path) {
		return MemoryStoreFile.of(this, path, false);
	}
	
	@Override
	public FileStoreFile fileReference(CommonPath path, boolean isFolder) {
		return MemoryStoreFile.of(this, path, isFolder);
	}
	
	@Override
	public void getFolderListing(CommonPath path, OperationOutcome<List<FileStoreFile>> callback) {
		MemoryStoreFile f = MemoryStoreFile.of(this, path, true);
		
		f.getFolderListing(callback);
	}
}
