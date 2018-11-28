package dcraft.filevault.work;

import dcraft.db.BasicRequestContext;
import dcraft.db.DatabaseAdapter;
import dcraft.db.DatabaseException;
import dcraft.db.IConnectionManager;
import dcraft.db.fileindex.FileIndexAdapter;
import dcraft.filestore.FileStoreFile;
import dcraft.filevault.IndexTransaction;
import dcraft.filevault.Vault;
import dcraft.hub.ResourceHub;
import dcraft.hub.app.ApplicationHub;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
import dcraft.hub.op.OperationOutcome;
import dcraft.log.Logger;
import dcraft.struct.RecordStruct;
import dcraft.task.StateWork;
import dcraft.task.StateWorkStep;
import dcraft.task.TaskContext;
import dcraft.tenant.Site;
import dcraft.tenant.Tenant;
import dcraft.tenant.TenantHub;

import java.math.BigDecimal;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

abstract public class IndexFilesCoreWork extends StateWork {
	protected Deque<Site> sites = new ArrayDeque<>();
	protected Deque<Vault> vaults = new ArrayDeque<>();
	protected Deque<FileStoreFile> folders = new ArrayDeque<>();
	
	protected Site currentSite = null;
	protected Vault currentVault = null;
	protected IndexTransaction tx = null;
	
	protected StateWorkStep indexSite = null;
	protected StateWorkStep indexFolder = null;
	protected StateWorkStep indexVault = null;
	protected StateWorkStep done = null;
	
	protected FileIndexAdapter adapter = null;
	
	public StateWorkStep prepSites(TaskContext trun) throws OperatingContextException {
		if (! ResourceHub.getResources().getDatabases().hasDefaultDatabase()) {
			Logger.error("No local database, cannot index vault files");
			return StateWorkStep.STOP;
		}
		
		IConnectionManager connectionManager = ResourceHub.getResources().getDatabases().getDatabase();
		
		this.adapter = FileIndexAdapter.of(BasicRequestContext.of(connectionManager.allocateAdapter()));
		
		for (Tenant tenant : TenantHub.getTenants())
			for (Site site : tenant.getSites())
				this.sites.addLast(site);
		
		return this.indexSite;
	}
	
	public StateWorkStep doSite(TaskContext trun) throws OperatingContextException {
		Site site = this.sites.pollFirst();
		
		if (site == null)
			return this.done;
		
		this.currentSite = site;
		
		for (Vault vault : site.getVaults())
			this.vaults.addLast(vault);
		
		return this.indexVault;
	}
	
	public StateWorkStep doVault(TaskContext trun) throws OperatingContextException {
		Vault vault = this.vaults.pollFirst();
		
		if (vault == null)
			return this.indexSite;
		
		this.currentVault = vault;
		this.folders.addLast(vault.getFileStore().rootFolder());
		
		this.adapter.clearVaultIndex(this.currentVault);
		
		this.tx = IndexTransaction.of(vault);
		
		return this.indexFolder;
	}
	
	public StateWorkStep doFolder(TaskContext trun) throws OperatingContextException {
		OperationContext.getOrThrow().touch();
		
		FileStoreFile folder = this.folders.pollFirst();
		
		if (folder == null) {
			// no more folders so go back to checking vaults, but first commit any files in the tx
			this.tx.commit();
			
			return this.indexVault;
		}
		
		folder.getFolderListing(new OperationOutcome<List<FileStoreFile>>() {
			@Override
			public void callback(List<FileStoreFile> result) throws OperatingContextException {
				if (result != null) {
					for (FileStoreFile file : result) {
						System.out.println(" - " + file.isFolder() + " : " + file);
						
						if (file.isFolder())
							IndexFilesCoreWork.this.folders.addLast(file);
						else
							IndexFilesCoreWork.this.tx.withUpdate(file.getPathAsCommon());
					}
				}
				
				trun.resume();	// try next folder
			}
		});
		
		return StateWorkStep.WAIT;
	}
	
	public StateWorkStep done(TaskContext trun) throws OperatingContextException {
		return StateWorkStep.NEXT;
	}
}
