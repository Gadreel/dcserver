package dcraft.filevault;

import dcraft.filestore.CommonPath;
import dcraft.filestore.local.LocalStore;
import dcraft.filestore.local.LocalStoreFile;
import dcraft.hub.app.ApplicationHub;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
import dcraft.hub.op.OperationOutcomeEmpty;
import dcraft.log.Logger;
import dcraft.stream.StreamWork;
import dcraft.stream.file.TarStream;
import dcraft.struct.ListStruct;
import dcraft.struct.RecordStruct;
import dcraft.task.Task;
import dcraft.task.TaskContext;
import dcraft.task.TaskHub;
import dcraft.task.TaskObserver;
import dcraft.util.FileUtil;

import java.io.IOException;
import java.nio.file.FileVisitOption;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.EnumSet;

public class Transaction extends TransactionBase {
	static public Transaction of(String vaultname) {
		Transaction tx = new Transaction();
		tx.id = Transaction.createTransactionId();
		tx.vaultname = vaultname;
		tx.nodeid = ApplicationHub.getNodeId();
		return tx;
	}
	
	protected String vaultname = null;
	
	@Override
	public String getVaultName() {
		if (this.vault != null)
			return super.getVaultName();
		
		return this.vaultname;
	}
	
	public RecordStruct getManifest() throws OperatingContextException {
		return RecordStruct.record()
				.with("TimeStamp", this.timestamp)
				.with("Type", "Deposit")
				.with("Tenant", OperationContext.getOrThrow().getTenant().getAlias())
				.with("Site", OperationContext.getOrThrow().getSite().getAlias())
				.with("Vault", this.getVaultName())
				.with("Write", ListStruct.list().withCollection(this.updatelist))
				.with("Clean", this.cleanfolder)
				.with("Delete", ListStruct.list().withCollection(this.deletelist));
	}
	
	/*
	 * if this returns successfully then the deposit is persisted and will be sync'ed unless the current node is
	 * completely destroyed before such sync can happen
	 */
	public void commitTransaction(OperationOutcomeEmpty callback) throws OperatingContextException {
		this.buildUpdateList();
		
		LocalStore dstore = this.getFolder();
		
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
							// if there is a vault and it is expandable this will move the files
							if (Transaction.this.vault != null)
								Transaction.this.vault.beforeSubmitTransaction(Transaction.this);

							// delete the expanded temp files remaining, if any, and the folder
							FileUtil.deleteDirectory(dstore.getPath());

							// vault in name only, not function (server backup)
							Transaction.this.depositid = DepositHub.submitVaultDeposit(Transaction.this);
							
							// if there is a vault process (index) as needed
							if (Transaction.this.vault != null)
								Transaction.this.vault.processTransaction(Transaction.this);
							
							// don't update deposit index here, let it happen in build deposit
						}
						catch (OperatingContextException x) {
							Logger.error("Missing OC - unexpected - " + x);
						}
						
						callback.returnEmpty();
					}
				});
	}
	
	public void buildUpdateList() {
		Path source = this.getFolder().getPath();
		
		try {
			if (Files.exists(source)) {
				Files.walkFileTree(source, EnumSet.of(FileVisitOption.FOLLOW_LINKS), Integer.MAX_VALUE,
						new SimpleFileVisitor<Path>() {
							@Override
							public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
								if (file.endsWith(".DS_Store"))
									return FileVisitResult.CONTINUE;
								
								Path dest = source.relativize(file);
								
								Transaction.this.updatelist.add(CommonPath.from("/" + dest.toString()));
								
								return FileVisitResult.CONTINUE;
							}
						});
			}
		}
		catch (IOException x) {
			Logger.error("Error copying file tree: " + x);
		}
	}
	
	public void rollbackTransaction(OperationOutcomeEmpty callback) throws OperatingContextException {
		// delete the expanded temp files
		FileUtil.deleteDirectory(this.getFolder().getPath());
	}
}
