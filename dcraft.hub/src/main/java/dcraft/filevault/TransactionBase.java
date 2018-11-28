package dcraft.filevault;

import dcraft.filestore.CommonPath;
import dcraft.filestore.FileStore;
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
	protected List<CommonPath> deletelist = new ArrayList<>();
	protected List<CommonPath> updatelist = new ArrayList<>();

	public List<CommonPath> getDeletelist() {
		return this.deletelist;
	}

	public TransactionBase withDelete(CommonPath... paths) {
		for (CommonPath p : paths)
			this.deletelist.add(p);
			
		return this;
	}

	public TransactionBase withUpdate(CommonPath... paths) {
		for (CommonPath p : paths)
			this.updatelist.add(p);
			
		return this;
	}

	// assumes the transaction is expanded
	public List<CommonPath> getUpdateList() {
		return this.updatelist;
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
