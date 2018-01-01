package dcraft.filevault;

import dcraft.filestore.CommonPath;
import dcraft.filestore.FileStore;
import dcraft.filestore.local.LocalStore;
import dcraft.filestore.local.LocalStoreFile;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationOutcomeEmpty;
import dcraft.hub.op.OperationOutcomeStruct;
import dcraft.log.Logger;
import dcraft.stream.StreamFragment;
import dcraft.stream.StreamWork;
import dcraft.stream.file.TarStream;
import dcraft.struct.RecordStruct;
import dcraft.task.Task;
import dcraft.task.TaskContext;
import dcraft.task.TaskHub;
import dcraft.task.TaskObserver;
import dcraft.util.FileUtil;
import dcraft.util.RndUtil;

import java.nio.file.Files;

public class Transaction {
	static public String createTransactionId() {
		return RndUtil.nextUUId();  // token is protected by session - session id is secure random
	}
	
	static public Transaction tx() {
		Transaction tx = new Transaction();
		tx.id = Transaction.createTransactionId();
		return tx;
	}
	
	static public Transaction of(String transactionid, Vault vault) {
		Transaction tx = new Transaction();
		tx.id = transactionid;
		tx.vault = vault;
		return tx;
	}
	
	static public Transaction of(Vault vault) {
		Transaction tx = new Transaction();
		tx.id = Transaction.createTransactionId();
		tx.vault = vault;
		return tx;
	}
	
	protected String id = null;
	protected Vault vault = null;
	
	public LocalStore getFolder() {
		return LocalStore.of(DepositHub.DepositStore.resolvePath("/transactions/" + this.id));
	}
	
	/*
	 * if this returns successfully then the deposit is persisted and will be sync'ed unless the current node is
	 * completely destroyed before such sync can happen
	 */
	public void commitTransaction(String vault, OperationOutcomeEmpty callback) throws OperatingContextException {
		LocalStore dstore = LocalStore.of(DepositHub.DepositStore.resolvePath("/transactions/" + this.id));
		
		TaskHub.submit(Task.ofSubtask("Create Transaction Tar", "XFR")
				.withWork(StreamWork.of(
						dstore.rootFolder().allocStreamSrc(),
						new TarStream().withNameHint(this.id),
						LocalStoreFile.of(DepositHub.DepositStore, CommonPath.from("/outstanding"), true).allocStreamDest()
				)),
				new TaskObserver() {
					@Override
					public void callback(TaskContext subtask) {
						try {
							if (Transaction.this.vault != null) {
								if (Transaction.this.vault.getMode() == VaultMode.Expand) {
									FileStore vfs = Transaction.this.vault.getFileStore();
									
									if (vfs instanceof LocalStore) {
										FileUtil.moveFileTree(dstore.getPath(), ((LocalStore) vfs).getPath(), null);
										
										FileUtil.deleteDirectory(dstore.getPath());
									}
									else {
										// TODO add Expand for non-local vaults - probably just to the deposit worker since
										// non-local vaults are not guaranteed to be epxanded at end of this call
										Logger.error("Non-local Expand Vaults not yet supported!");
										callback.returnEmpty();
										return;
									}
								}
								
								// TODO support other vault modes
								
								DepositHub.submitVaultDeposit(Transaction.this.id, Transaction.this.vault.getName());
							}
							else {
								FileUtil.deleteDirectory(dstore.getPath());
								
								DepositHub.submitVaultDeposit(Transaction.this.id, vault);
							}
						}
						catch (OperatingContextException x) {
							Logger.error("Missing OC - unexpected - " + x);
						}
						
						callback.returnEmpty();
					}
				});
	}
	
	public void rollbackTransaction(OperationOutcomeEmpty callback) throws OperatingContextException {
		// delete files in tx folder
		LocalStore dstore = LocalStore.of(DepositHub.DepositStore.resolvePath("/transactions/" + this.id));
		
		dstore.removeFolder(CommonPath.ROOT, callback);
	}
	
	public void commitInternalTransaction() throws OperatingContextException {
		LocalStore dstore = LocalStore.of(DepositHub.DepositStore.resolvePath("/transactions/" + this.id));
		if (Transaction.this.vault != null) {
			// TODO support other vault modes
			
			if (Transaction.this.vault.getMode() == VaultMode.Expand) {
				FileStore vfs = Transaction.this.vault.getFileStore();
				
				if (vfs instanceof LocalStore) {
					FileUtil.moveFileTree(dstore.getPath(), ((LocalStore) vfs).getPath(), null);
					
					FileUtil.deleteDirectory(dstore.getPath());
				}
				else {
					// TODO add Expand for non-local vaults - probably just to the deposit worker since
					// non-local vaults are not guaranteed to be epxanded at end of this call
					Logger.error("Non-local Expand Vaults not yet supported!");
				}
			}
		}
	}
}
