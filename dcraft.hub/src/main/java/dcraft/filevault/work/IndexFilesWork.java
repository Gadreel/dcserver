package dcraft.filevault.work;

import dcraft.db.DatabaseAdapter;
import dcraft.db.DatabaseException;
import dcraft.db.IConnectionManager;
import dcraft.db.util.ByteUtil;
import dcraft.filestore.FileStoreFile;
import dcraft.filevault.Vault;
import dcraft.hub.ResourceHub;
import dcraft.hub.app.ApplicationHub;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
import dcraft.hub.op.OperationOutcome;
import dcraft.hub.resource.ConfigResource;
import dcraft.hub.resource.ResourceTier;
import dcraft.log.Logger;
import dcraft.struct.RecordStruct;
import dcraft.struct.Struct;
import dcraft.task.IWork;
import dcraft.task.StateWork;
import dcraft.task.StateWorkStep;
import dcraft.task.TaskContext;
import dcraft.tenant.Site;
import dcraft.tenant.Tenant;
import dcraft.tenant.TenantHub;
import dcraft.xml.XElement;
import dcraft.xml.XmlReader;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

public class IndexFilesWork extends StateWork {
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
	
	@Override
	public void prepSteps(TaskContext trun) throws OperatingContextException {
		this
				.withStep(StateWorkStep.of("Prep Sites List", this::prepSites))
				.withStep(indexSite = StateWorkStep.of("Index Site", this::doSite))
				.withStep(indexVault = StateWorkStep.of("Index Vault", this::doVault))
				.withStep(indexFolder = StateWorkStep.of("Index Folder", this::doFolder))
				.withStep(done = StateWorkStep.of("Done", this::done));
	}
	
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
			IndexFilesWork.this.adapter.kill(IndexFilesWork.this.currentSite.getTenant().getAlias(), "dcFileIndex",
					IndexFilesWork.this.currentSite.getAlias(), IndexFilesWork.this.currentVault.getName());
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
								IndexFilesWork.this.folders.addLast(file);
								continue;
							}
							
							List<Object> indexkeys = new ArrayList<>();
							
							indexkeys.add(IndexFilesWork.this.currentSite.getTenant().getAlias());
							indexkeys.add("dcFileIndex");
							indexkeys.add(IndexFilesWork.this.currentSite.getAlias());
							indexkeys.add(IndexFilesWork.this.currentVault.getName());
							
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
							IndexFilesWork.this.adapter.set(indexkeys.toArray());
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
