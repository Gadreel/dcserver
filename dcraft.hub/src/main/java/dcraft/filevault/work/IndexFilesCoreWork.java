package dcraft.filevault.work;

import dcraft.db.DatabaseAdapter;
import dcraft.db.DatabaseException;
import dcraft.db.IConnectionManager;
import dcraft.filestore.FileStoreFile;
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
	
	protected StateWorkStep indexSite = null;
	protected StateWorkStep indexFolder = null;
	protected StateWorkStep indexVault = null;
	protected StateWorkStep done = null;
	
	protected DatabaseAdapter adapter = null;
	
	public StateWorkStep prepSites(TaskContext trun) throws OperatingContextException {
		if (! ResourceHub.getResources().getDatabases().hasDefaultDatabase()) {
			Logger.error("No local database, cannot index vault files");
			return StateWorkStep.STOP;
		}
		
		IConnectionManager connectionManager = ResourceHub.getResources().getDatabases().getDatabase();
		
		this.adapter = connectionManager.allocateAdapter();
		
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
		
		try {
			IndexFilesCoreWork.this.adapter.kill(IndexFilesCoreWork.this.currentSite.getTenant().getAlias(), "dcFileIndex",
					IndexFilesCoreWork.this.currentSite.getAlias(), IndexFilesCoreWork.this.currentVault.getName());
		}
		catch (DatabaseException x) {
			Logger.error("Unable to index file in db: " + x);
		}
		
		return this.indexFolder;
	}
	
	public StateWorkStep doFolder(TaskContext trun) throws OperatingContextException {
		OperationContext.getOrThrow().touch();
		
		FileStoreFile folder = this.folders.pollFirst();
		
		if (folder == null)
			return this.indexVault;

		folder.getFolderListing(new OperationOutcome<List<FileStoreFile>>() {
			@Override
			public void callback(List<FileStoreFile> result) throws OperatingContextException {
				if (result != null) {
					try {
						for (FileStoreFile file : result) {
							if (file.isFolder()) {
								IndexFilesCoreWork.this.folders.addLast(file);
								continue;
							}
							// set entry marker
							List<Object> indexkeys = new ArrayList<>();
							
							indexkeys.add(IndexFilesCoreWork.this.currentSite.getTenant().getAlias());
							indexkeys.add("dcFileIndex");
							indexkeys.add(IndexFilesCoreWork.this.currentSite.getAlias());
							indexkeys.add(IndexFilesCoreWork.this.currentVault.getName());
							
							for (String part : file.getPathAsCommon().getParts())
								indexkeys.add(part);
							
							indexkeys.add(true);
							
							adapter.set(indexkeys.toArray());
							
							// add state
							indexkeys = new ArrayList<>();
							
							indexkeys.add(IndexFilesCoreWork.this.currentSite.getTenant().getAlias());
							indexkeys.add("dcFileIndex");
							indexkeys.add(IndexFilesCoreWork.this.currentSite.getAlias());
							indexkeys.add(IndexFilesCoreWork.this.currentVault.getName());
							
							for (String part : file.getPathAsCommon().getParts())
								indexkeys.add(part);
							
							indexkeys.add("State");
							indexkeys.add(BigDecimal.ZERO);
							indexkeys.add("Present");
							
							// don't use  ByteUtil.dateTimeToReverse(file.getModificationAsTime()) - using zero is better for eventual consistency across nodes
							IndexFilesCoreWork.this.adapter.set(indexkeys.toArray());
							
							// add history
							indexkeys = new ArrayList<>();
							
							indexkeys.add(IndexFilesCoreWork.this.currentSite.getTenant().getAlias());
							indexkeys.add("dcFileIndex");
							indexkeys.add(IndexFilesCoreWork.this.currentSite.getAlias());
							indexkeys.add(IndexFilesCoreWork.this.currentVault.getName());
							
							for (String part : file.getPathAsCommon().getParts())
								indexkeys.add(part);
							
							indexkeys.add("History");
							indexkeys.add(BigDecimal.ZERO);
							indexkeys.add(RecordStruct.record()
									.with("Source", "Scan")
									.with("Op", "Write")
									.with("TimeStamp", file.getModification())
									.with("Node", ApplicationHub.getNodeId())
							);
							
							// don't use  ByteUtil.dateTimeToReverse(file.getModificationAsTime()) - using zero is better for eventual consistency across nodes
							IndexFilesCoreWork.this.adapter.set(indexkeys.toArray());
						}
					}
					catch (DatabaseException x) {
						Logger.error("Unable to index file in db: " + x);
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
