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
package dcraft.filevault;

import dcraft.filestore.*;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationOutcome;
import dcraft.hub.op.OperationOutcomeEmpty;
import dcraft.log.Logger;
import dcraft.schema.SchemaHub;
import dcraft.script.work.ReturnOption;
import dcraft.script.work.StackWork;
import dcraft.struct.RecordStruct;
import dcraft.util.io.IFileWatcher;
import dcraft.xml.XElement;

import java.util.ArrayList;
import java.util.List;

public class EncryptedVaultStore extends FileStore {
	static public EncryptedVaultStore of(EncryptedVault vault) {
		EncryptedVaultStore driver = new EncryptedVaultStore();
		driver.vault = vault;

		driver.with("RootFolder", "/");
		driver.connect(null, null);

		return driver;
	}

	protected EncryptedVault vault = null;
	protected List<IFileWatcher> observers = new ArrayList<>();

	public EncryptedVaultStore() {
		this.withType(SchemaHub.getType("dcEncryptedVaultStore"));
	}
	
	@Override
	public EncryptedVaultStore deepCopy() {
		EncryptedVaultStore cp = new EncryptedVaultStore();
		this.doCopy(cp);
		return cp;
	}

	@Override
	public boolean close(OperationOutcomeEmpty callback) {
		if (callback != null)
			callback.returnResult();

		return true;
	}

	@Override
	public void connect(RecordStruct params, OperationOutcomeEmpty callback) {
		if (callback != null)
			callback.returnResult();
	}

	@Override
	public ReturnOption operation(StackWork stack, XElement codeEl) throws OperatingContextException {
		// connect and close in super

		/* TODO
		if ("Delete".equals(codeEl.getName())) {
			FileUtil.deleteDirectory(Paths.get(this.getFieldAsString("RootFolder")));
			
			return ReturnOption.CONTINUE;
		}
		
		if ("MakeDir".equals(codeEl.getName())) {
			FileUtil.confirmOrCreateDir(Paths.get(this.getFieldAsString("RootFolder")));
			return ReturnOption.CONTINUE;
		}
		
		if ("GetInfo".equals(codeEl.getName())) {
			String path = StackUtil.stringFromElement(stack, codeEl, "Path");

			if (StringUtil.isEmpty(path)) {
				Logger.error("Missing path fpr GetInfo");
				return ReturnOption.CONTINUE;
			}
			
			boolean absolute = StackUtil.boolFromElement(stack, codeEl, "Absolute", false);
			
	        String handle = StackUtil.stringFromElement(stack, codeEl, "Handle");

			if (handle != null) 
				if (absolute)
					StackUtil.addVariable(stack, handle,
							LocalStoreFile.of(EncryptedStore.this, Paths.get(path)));
				else
					StackUtil.addVariable(stack, handle,
							LocalStoreFile.of(EncryptedStore.this, RecordStruct.record().with("Path", path)));
			
			return ReturnOption.CONTINUE;
		}
		*/

		return super.operation(stack, codeEl);
	}

	@Override
	public void getFileDetail(CommonPath path, OperationOutcome<FileStoreFile> callback) throws OperatingContextException {
		this.vault.getFileDetail(path, null, new OperationOutcome<FileDescriptor>() {
			@Override
			public void callback(FileDescriptor result) {
				callback.returnValue((FileStoreFile) result);
			}
		});
	}

	@Override
	public void addFolder(CommonPath path, OperationOutcome<FileStoreFile> callback) throws OperatingContextException {
		this.vault.addFolder(path, null, new OperationOutcome<FileDescriptor>() {
			@Override
			public void callback(FileDescriptor result) throws OperatingContextException {
				callback.returnValue((FileStoreFile) result);
			}
		});
	}
	
	@Override
	public void removeFolder(CommonPath path, OperationOutcomeEmpty callback) throws OperatingContextException {
		this.vault.getFileDetail(path, null, new OperationOutcome<FileDescriptor>() {
			@Override
			public void callback(FileDescriptor result) throws OperatingContextException {
				if (result.exists() && result.isFolder()) {
					vault.deleteFile(result, null, callback);
				}
				else {
					callback.returnEmpty();
				}
			}
		});
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
		//return LocalStoreFile.of(this, path).scanner();
		Logger.error("Scanner not supported for encrypted file store");
		return null;
	}

	@Override
	public FileStoreFile rootFolder() {
		return EncryptedVaultFile.reference(this, CommonPath.ROOT, true);
	}
	
	@Override
	public FileStoreFile fileReference(CommonPath path) {
		return EncryptedVaultFile.reference(this, path, false);
	}
	
	@Override
	public FileStoreFile fileReference(CommonPath path, boolean isFolder) {
		return EncryptedVaultFile.reference(this, path, isFolder);
	}
	
	@Override
	public void getFolderListing(CommonPath path, OperationOutcome<List<FileStoreFile>> callback) throws OperatingContextException {
		this.vault.getFileDetail(path, null, new OperationOutcome<FileDescriptor>() {
			@Override
			public void callback(FileDescriptor result) throws OperatingContextException {
				if (result.exists() && result.isFolder()) {
					((FileStoreFile) result).getFolderListing(callback);
				}
				else {
					callback.returnEmpty();
				}
			}
		});
	}
}
