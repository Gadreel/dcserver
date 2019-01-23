package dcraft.filevault;

import dcraft.db.BasicRequestContext;
import dcraft.db.DatabaseException;
import dcraft.db.IRequestContext;
import dcraft.db.fileindex.BasicFilter;
import dcraft.db.fileindex.FileIndexAdapter;
import dcraft.db.proc.ExpressionResult;
import dcraft.db.util.ByteUtil;
import dcraft.filestore.CommonPath;
import dcraft.filestore.FileDescriptor;
import dcraft.filestore.local.LocalStore;
import dcraft.filestore.local.LocalStoreFile;
import dcraft.filevault.work.ExpandDepositWork;
import dcraft.hub.ResourceHub;
import dcraft.hub.app.ApplicationHub;
import dcraft.hub.op.*;
import dcraft.hub.resource.KeyRingResource;
import dcraft.log.Logger;
import dcraft.stream.StreamFragment;
import dcraft.stream.file.FilterStream;
import dcraft.struct.ListStruct;
import dcraft.struct.RecordStruct;
import dcraft.struct.Struct;
import dcraft.struct.scalar.StringStruct;
import dcraft.util.IOUtil;
import dcraft.util.TimeUtil;
import dcraft.util.chars.Utf8Encoder;
import dcraft.util.pgp.ClearsignUtil;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

public class EncryptedVault extends Vault {
	protected EncryptedVaultStore estore = EncryptedVaultStore.of(this);

	/*
	 * ================ internal features ==================
	 */
	
	@Override
	public void getFileDetail(CommonPath path, RecordStruct params, OperationOutcome<FileDescriptor> fcb) throws OperatingContextException {
		BasicRequestContext dbctx = BasicRequestContext.ofDefaultDatabase();
		
		FileDescriptor fd = this.getDetail(dbctx, path);

		if (fd == null)
			fd = new FileDescriptor().withPath(path).withExists(false).withConfirmed(true);

		fcb.returnValue(fd);
	}

	@Override
	public void addFolder(CommonPath path, RecordStruct params, OperationOutcome<FileDescriptor> callback) throws OperatingContextException {
		// no transactions, folders are not tracked
		BasicRequestContext dbctx = BasicRequestContext.ofDefaultDatabase();
		
		FileIndexAdapter adapter = FileIndexAdapter.of(dbctx);
		
		adapter.indexFolderEnsure(this, path);
		
		callback.returnValue(this.getDetail(dbctx, path));
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
				
				BasicRequestContext dbctx = BasicRequestContext.ofDefaultDatabase();
				
				FileIndexAdapter adapter = FileIndexAdapter.of(dbctx);
				
				Transaction ftx = buildUpdateTransaction(Transaction.createTransactionId(), params);
				
				// if folder collect all files in a folder and add to Tx for processing later.
				// then here do a quick entry that marks only the folder as deleted and mark it as User not Scan
				// this is to give immediate feedback to a user that deletes a folder
				// processing the Tx will result in marking all sub files with the transaction id - so no need to do that here
	
				if (file.isFolder()) {
					adapter.traverseIndex(EncryptedVault.this, file.getPathAsCommon(), -1, OperationContext.getOrThrow(), new BasicFilter() {
						@Override
						public ExpressionResult check(FileIndexAdapter adapter, IVariableAware scope, Vault vault, CommonPath path, RecordStruct file) throws OperatingContextException {
							ftx.withDelete(path);
							
							return ExpressionResult.ACCEPTED;
						}
					});
					
					// mark the folder so it is hidden from user, no other point
					adapter.hideFolder(EncryptedVault.this, file.getPathAsCommon(), TimeUtil.now(),
							buildHistory(ftx, "Delete").with("Source", "User"));
				}
				else {
					ftx.withDelete(file.getPathAsCommon());
				}
				
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
		BasicRequestContext dbctx = BasicRequestContext.ofDefaultDatabase();
		
		List<FileDescriptor> files = new ArrayList<>();
		
		try {
			List<Object> indexkeys = FileIndexAdapter.pathToIndex(this, file.getPathAsCommon());
			
			// start at top
			indexkeys.add(null);
			
			byte[] pkey = dbctx.getInterface().nextPeerKey(indexkeys.toArray());
			
			while (pkey != null) {
				Object pval = ByteUtil.extractValue(pkey);
				
				if (pval instanceof String) {
					CommonPath entrypath = file.getPathAsCommon().resolve((String) pval);
					
					FileDescriptor fd = this.getDetail(dbctx, entrypath);
					
					if ((fd != null) && fd.confirmed() && fd.exists()) {
						files.add(fd);
						
						// TODO it would be nice to have size too
					}
				}
				
				indexkeys.remove(indexkeys.size() - 1);
				indexkeys.add(pval);
				
				pkey = dbctx.getInterface().nextPeerKey(indexkeys.toArray());
			}
			
			callback.returnValue(files);
		}
		catch (DatabaseException x) {
			Logger.error("Unable to list folder " + file.getPath() + " in db: " + x);
			
			callback.returnEmpty();
		}
	}
	
	public FileDescriptor getDetail(IRequestContext dbctx, CommonPath path) throws OperatingContextException {
		FileIndexAdapter fiadapter = FileIndexAdapter.of(dbctx);
		
		RecordStruct info = fiadapter.fileInfo(this, path, OperationContext.getOrThrow());
		
		if (info != null)
			return EncryptedVaultFile.ofFileIndex(this.estore, path, info);

		return null;
	}

	@Override
	public void renameFiles(FileDescriptor file, ListStruct renames, RecordStruct params, OperationOutcomeEmpty callback) {
		Logger.error("Not currently supported on encrypted vaults.");
		callback.returnEmpty();
	}

	@Override
	public void moveFiles(FileDescriptor file, FileDescriptor dest, RecordStruct params, OperationOutcomeEmpty callback) {
		Logger.error("Not currently supported on encrypted vaults.");
		callback.returnEmpty();
	}

	@Override
	public StreamFragment toSourceStream(FileDescriptor fileDescriptor) throws OperatingContextException {
		BasicRequestContext dbctx = BasicRequestContext.ofDefaultDatabase();

		FileIndexAdapter adapter = FileIndexAdapter.of(dbctx);

		RecordStruct hist = adapter.fileDeposit(this, fileDescriptor.getPathAsCommon(), OperationContext.getOrThrow());

		if (hist != null) {
			StreamFragment source = DepositHub.expandDepositFragment(hist.getFieldAsString("Node"), hist.getFieldAsString("Deposit"));
			
			if (source != null) {
				source.withAppend(FilterStream.of(fileDescriptor.getName()));
				return source;
			}
		}

		Logger.error("Could not find file in vault.");
		return null;
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
