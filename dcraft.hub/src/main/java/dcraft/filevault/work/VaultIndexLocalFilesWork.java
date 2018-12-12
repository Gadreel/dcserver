package dcraft.filevault.work;

import dcraft.db.BasicRequestContext;
import dcraft.db.DatabaseAdapter;
import dcraft.db.DatabaseException;
import dcraft.db.IConnectionManager;
import dcraft.db.fileindex.BasicFilter;
import dcraft.db.fileindex.FileIndexAdapter;
import dcraft.db.proc.ExpressionResult;
import dcraft.db.util.ByteUtil;
import dcraft.filestore.CommonPath;
import dcraft.filestore.FileDescriptor;
import dcraft.filestore.FileStore;
import dcraft.filestore.local.LocalStore;
import dcraft.filevault.FileStoreVault;
import dcraft.filevault.IndexTransaction;
import dcraft.filevault.Vault;
import dcraft.hub.ResourceHub;
import dcraft.hub.op.IVariableAware;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
import dcraft.hub.op.OperationOutcome;
import dcraft.hub.op.OperationOutcomeStruct;
import dcraft.log.Logger;
import dcraft.struct.ListStruct;
import dcraft.struct.RecordStruct;
import dcraft.struct.Struct;
import dcraft.struct.builder.BuilderStateException;
import dcraft.task.StateWork;
import dcraft.task.StateWorkStep;
import dcraft.task.TaskContext;
import dcraft.tenant.Site;
import dcraft.tenant.Tenant;
import dcraft.tenant.TenantHub;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

public class VaultIndexLocalFilesWork extends StateWork {
	static public VaultIndexLocalFilesWork of(FileStoreVault vault) {
		VaultIndexLocalFilesWork work = new VaultIndexLocalFilesWork();
		work.currentVault = vault;
		return work;
	}
	
	protected Deque<FileDescriptor> folders = new ArrayDeque<>();
	
	protected FileStoreVault currentVault = null;
	protected IndexTransaction tx = null;
	protected DatabaseAdapter adapter = null;
	
	protected StateWorkStep indexFolder = null;
	protected StateWorkStep removeUpdate = null;
	
	@Override
	public void prepSteps(TaskContext trun) throws OperatingContextException {
		this
				.withStep(indexFolder = StateWorkStep.of("Index Folder", this::doFolder))
				.withStep(removeUpdate = StateWorkStep.of("Remove if not local", this::doRemove));
		
		this.adapter = ResourceHub.getResources().getDatabases().getDatabase().allocateAdapter();
		
		this.folders.addLast(FileDescriptor.of("/"));
		
		this.tx = IndexTransaction.of(this.currentVault);
	}
	
	public StateWorkStep doFolder(TaskContext trun) throws OperatingContextException {
		OperationContext.getOrThrow().touch();
		
		FileDescriptor folder = this.folders.pollFirst();
		
		if (folder == null) {
			return this.removeUpdate;
		}
		
		// use vault, not file store, to list files - this way we only index the files significant to the vault
		this.currentVault.getFolderListing(folder, null, new OperationOutcome<List<? extends FileDescriptor>>() {
			@Override
			public void callback(List<? extends FileDescriptor> files) throws OperatingContextException {
				if (! this.hasErrors()) {
					for (FileDescriptor file : files) {
						System.out.println(" - " + file.isFolder() + " : " + file.getName());
						
						if (file.getFieldAsBoolean("IsFolder"))
							VaultIndexLocalFilesWork.this.folders.addLast(file);
						else
							VaultIndexLocalFilesWork.this.tx.withUpdate(file.getPathAsCommon());
					}
				}
				
				trun.resume();
			}
		});
		
		return StateWorkStep.WAIT;
	}
	
	public StateWorkStep doRemove(TaskContext trun) throws OperatingContextException {
		FileIndexAdapter fiadapter = FileIndexAdapter.of(BasicRequestContext.of(this.adapter));
		IVariableAware scope = OperationContext.getOrThrow();
		
		fiadapter.traverseIndex(this.currentVault, CommonPath.ROOT, (int) -1, scope, new BasicFilter() {
			@Override
			public ExpressionResult check(FileIndexAdapter adapter, IVariableAware scope, Vault vault, CommonPath path, RecordStruct file) throws OperatingContextException {
				if ("Present".equals(file.getFieldAsString("State"))) {
					FileStore fs = currentVault.getFileStore();
					
					if (fs instanceof LocalStore) {
						Path npath = ((LocalStore) fs).resolvePath(path);
						
						if (Files.notExists(npath))
							tx.withDelete(path);
					}
				}
				
				return ExpressionResult.accepted();
			}
		});
		
		// finish
		// no more folders so go back to checking vaults, but first commit any files in the tx
		this.tx.commit();
		
		return StateWorkStep.STOP;
	}
}
