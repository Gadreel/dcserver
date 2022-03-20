package dcraft.filevault;

import dcraft.filestore.CommonPath;
import dcraft.filestore.FileStore;
import dcraft.filestore.FileStoreFile;
import dcraft.filestore.local.LocalStore;
import dcraft.filestore.local.LocalStoreFile;
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
import dcraft.tenant.Site;
import dcraft.util.FileUtil;
import dcraft.util.RndUtil;
import dcraft.util.TimeUtil;

import java.io.IOException;
import java.nio.file.FileVisitOption;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

abstract public class TransactionBase {
	static public String createTransactionId() {
		return RndUtil.nextUUId();  // token is protected by session - session id is secure random
	}
	
	protected String id = null;
	protected String depositid = null;
	protected String nodeid = null;
	protected Vault vault = null;
	protected ZonedDateTime timestamp = TimeUtil.now();
	protected List<TransactionFile> deletelist = new ArrayList<>();
	protected List<TransactionFile> updatelist = new ArrayList<>();
	protected CommonPath cleanfolder = null;

	public List<TransactionFile> getDeletelist() {
		return this.deletelist;
	}

	public TransactionBase withDelete(TransactionFile... paths) {
		for (TransactionFile p : paths)
			this.deletelist.add(p);
			
		return this;
	}

	public TransactionBase withUpdate(TransactionFile... paths) {
		for (TransactionFile p : paths)
			this.updatelist.add(p);
			
		return this;
	}

	public TransactionFile pathToTransactionFile(Vault vault, CommonPath path) throws OperatingContextException {
		ZonedDateTime when = this.getTimestamp();

		if (vault instanceof FileStoreVault) {
			// blocking so only works with local file stores, but this is much better than nothing
			FileStoreFile filedesc = ((FileStoreVault) vault).getFileStore().fileReference(path);

			if ((filedesc != null) && filedesc.isNotFieldEmpty("Modified"))
				when = filedesc.selectAsDateTime("Modified");
		}

		return TransactionFile.of(path, when);
	}

	public List<TransactionFile> getUpdateList() {
		return this.updatelist;
	}
	
	public TransactionBase withCleanFolder(CommonPath v) {
		this.cleanfolder = v;
		return this;
	}
	
	public CommonPath getCleanFolder() {
		return this.cleanfolder;
	}
	
	public String getId() {
		return this.id;
	}
	
	public String getDepositId() {
		return this.depositid;
	}
	
	public String getNodeId() {
		return this.nodeid;
	}
	
	// prefer this over getVault when possible, more versitile
	public String getVaultName() {
		return this.vault.getName();
	}
	
	public Vault getVault() {
		return this.vault;
	}
	
	public ZonedDateTime getTimestamp() {
		return this.timestamp;
	}
	
	public LocalStore getFolder() {
		return LocalStore.of(DepositHub.DepositStore.resolvePath("/transactions/" + this.id));
	}
}
