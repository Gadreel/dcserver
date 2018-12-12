package dcraft.filevault;

import dcraft.db.BasicRequestContext;
import dcraft.db.DatabaseAdapter;
import dcraft.db.DatabaseException;
import dcraft.db.IConnectionManager;
import dcraft.db.fileindex.BasicFilter;
import dcraft.db.fileindex.FileIndexAdapter;
import dcraft.db.fileindex.IFilter;
import dcraft.db.proc.ExpressionResult;
import dcraft.db.util.ByteUtil;
import dcraft.filestore.CommonPath;
import dcraft.filestore.FileDescriptor;
import dcraft.filestore.FileStore;
import dcraft.filestore.FileStoreFile;
import dcraft.filestore.local.LocalStore;
import dcraft.filestore.local.LocalStoreFile;
import dcraft.filevault.work.VaultIndexLocalFilesWork;
import dcraft.hub.ResourceHub;
import dcraft.hub.app.ApplicationHub;
import dcraft.hub.op.IVariableAware;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
import dcraft.hub.op.OperationOutcome;
import dcraft.hub.op.OperationOutcomeEmpty;
import dcraft.hub.op.OperationOutcomeString;
import dcraft.hub.op.OperationOutcomeStruct;
import dcraft.hub.op.UserContext;
import dcraft.log.Logger;
import dcraft.stream.IStream;
import dcraft.stream.StreamFragment;
import dcraft.struct.FieldStruct;
import dcraft.struct.ListStruct;
import dcraft.struct.RecordStruct;
import dcraft.struct.Struct;
import dcraft.task.IWork;
import dcraft.task.TaskContext;
import dcraft.tenant.Site;
import dcraft.tenant.Tenant;
import dcraft.tenant.TenantHub;
import dcraft.util.FileUtil;
import dcraft.util.RndUtil;
import dcraft.util.StringUtil;
import dcraft.util.TimeUtil;
import dcraft.xml.XElement;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.zone.ZoneOffsetTransition;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.function.Consumer;

public class EncryptedVault extends Vault {
	/*
	 * ================ internal features ==================
	 */
	
	@Override
	public void getFileDetail(CommonPath path, RecordStruct params, OperationOutcome<FileDescriptor> fcb) throws OperatingContextException {
		IConnectionManager connectionManager = ResourceHub.getResources().getDatabases().getDatabase();
		
		DatabaseAdapter adapter = connectionManager.allocateAdapter();
		
		FileDescriptor fd = this.getDetail(adapter, path);
		
		fcb.returnValue(fd);
	}

	@Override
	public void addFolder(CommonPath path, RecordStruct params, OperationOutcome<FileDescriptor> callback) throws OperatingContextException {
		// no transactions, folders are not tracked
		IConnectionManager connectionManager = ResourceHub.getResources().getDatabases().getDatabase();
		
		FileIndexAdapter adapter = FileIndexAdapter.of(BasicRequestContext.of(connectionManager.allocateAdapter()));
		
		adapter.indexFolderEnsure(this, path);
		
		callback.returnEmpty();
	}
	
	@Override
	public void deleteFile(FileDescriptor file, RecordStruct params, OperationOutcomeEmpty callback) throws OperatingContextException {
		// check if `file` has info loaded
		if (! file.confirmed()) {
			Logger.error("Get file details first");
			callback.returnEmpty();
			return;
		}
		
		this.beforeRemove(file, params, new OperationOutcomeEmpty() {
			@Override
			public void callback() throws OperatingContextException {
				if (this.hasErrors() || ! file.exists()) {
					callback.returnEmpty();
					return;
				}
				
				IConnectionManager connectionManager = ResourceHub.getResources().getDatabases().getDatabase();
				
				FileIndexAdapter adapter = FileIndexAdapter.of(BasicRequestContext.of(connectionManager.allocateAdapter()));
				
				Transaction ftx = buildUpdateTransaction(Transaction.createTransactionId(), params);
				
				// TODO really only works on files for now, not folders
				// better approach - collect all files in a folder and add to Tx for processing later.
				// then here do a quick entry that marks only the folder as deleted and mark it as User not Scan
				// this is to give immediate feedback to a user that deletes a folder
				// processing the Tx will result in marking all sub files with the transaction id - so no need to do that here
	
				// mark the folder so it is hidden from user, no other point
				if (file.isFolder())
					adapter.deleteFile(EncryptedVault.this, file.getPathAsCommon(), TimeUtil.now(), buildHistory(ftx, "Delete"));
				
				// TODO if this was a folder, instead list all the files removed for a safer transaction
				// allows us to replay the transaction out of order
				ftx.withDelete(file.getPathAsCommon());
				
				ftx.commitTransaction(new OperationOutcomeEmpty() {
					@Override
					public void callback() throws OperatingContextException {
						afterRemove(file, params, callback);
					}
				});
			}
		});
	}
	
	@Override
	public void getFolderListing(FileDescriptor file, RecordStruct params, OperationOutcome<List<? extends FileDescriptor>> callback) throws OperatingContextException {
		IConnectionManager connectionManager = ResourceHub.getResources().getDatabases().getDatabase();
		
		DatabaseAdapter adapter = connectionManager.allocateAdapter();
		
		List<FileDescriptor> files = new ArrayList<>();
		
		try {
			List<Object> indexkeys = FileIndexAdapter.pathToIndex(this, file.getPathAsCommon());
			
			// start at top
			indexkeys.add(null);
			
			byte[] pkey = adapter.nextPeerKey(indexkeys.toArray());
			
			while (pkey != null) {
				Object pval = ByteUtil.extractValue(pkey);
				
				if (pval instanceof String) {
					CommonPath entrypath = file.getPathAsCommon().resolve((String) pval);
					
					FileDescriptor fd = this.getDetail(adapter, entrypath);
					
					if ((fd != null) && fd.confirmed() && fd.exists()) {
						files.add(fd);
						
						// TODO it would be nice to have size too
					}
				}
				
				indexkeys.remove(indexkeys.size() - 1);
				indexkeys.add(pval);
				
				pkey = adapter.nextPeerKey(indexkeys.toArray());
			}
			
			callback.returnValue(files);
		}
		catch (DatabaseException x) {
			Logger.error("Unable to list folder " + file.getPath() + " in db: " + x);
			
			callback.returnEmpty();
		}
	}
	
	public FileDescriptor getDetail(DatabaseAdapter adapter, CommonPath path) throws OperatingContextException {
		try {
			FileDescriptor fd = FileDescriptor.of(path.toString());
			
			List<Object> entrykeys = FileIndexAdapter.pathToIndex(this, path);
			
			Object marker = adapter.get(entrykeys.toArray());
			
			if ("Folder".equals(Struct.objectToString(marker))) {
				fd.withIsFolder(true).withExists(true).withConfirmed(true);
			}
			else if ("XFolder".equals(Struct.objectToString(marker))) {
				fd.withIsFolder(true).withExists(false).withConfirmed(true);
			}
			else if (marker != null) {
				// state
				entrykeys.add("State");
				entrykeys.add(null);
				
				byte[] ekey = adapter.nextPeerKey(entrykeys.toArray());
				
				if (ekey != null) {
					Object eval = ByteUtil.extractValue(ekey);
					
					entrykeys = FileIndexAdapter.pathToIndex(this, path);
					
					entrykeys.add("State");
					entrykeys.add(eval);
					
					BigDecimal epoch = Struct.objectToDecimal(eval).abs();
					
					fd.withModificationTime(ZonedDateTime.ofInstant(Instant.ofEpochMilli(epoch.longValue()), ZoneId.of("UTC")));
					
					if ("Present".equals(Struct.objectToString(adapter.get(entrykeys.toArray()))))
						fd.withExists(true);
				}
				
				fd.withConfirmed(true);
			}
			else {
				entrykeys.add(null);
				
				byte[] ekey = adapter.nextPeerKey(entrykeys.toArray());
				
				if (ekey != null) {
					// the folder is implied by path
					fd
							.withExists(true)
							.withIsFolder(true)
							.withConfirmed(true);
				}
			}
	
			return fd;
		}
		catch (DatabaseException x) {
			Logger.error("Unable to get file detail " + path + " in db: " + x);
		}
		
		return null;
	}
	
	@Override
	public StreamFragment toSourceStream(FileDescriptor fileDescriptor) {
		return null;	// TODO
	}
	
	@Override
	public void hashFile(FileDescriptor fileDescriptor, String evidence, RecordStruct params, OperationOutcomeString callback) {
		// TODO
	}
	
	@Override
	public Transaction buildUpdateTransaction(String txid, RecordStruct params) {
		// might change someday, for now use same as FileStoreVault does
		Transaction tx = new Transaction();
		
		tx.id = txid;
		tx.vault = this;
		tx.nodeid = ApplicationHub.getNodeId();
		
		return tx;
	}
}
