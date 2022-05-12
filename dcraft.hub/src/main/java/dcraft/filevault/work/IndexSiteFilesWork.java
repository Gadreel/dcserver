package dcraft.filevault.work;

import dcraft.db.BasicRequestContext;
import dcraft.db.DatabaseException;
import dcraft.db.IConnectionManager;
import dcraft.db.fileindex.FileIndexAdapter;
import dcraft.db.util.ByteUtil;
import dcraft.filestore.CommonPath;
import dcraft.filevault.Vault;
import dcraft.hub.ResourceHub;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
import dcraft.log.Logger;
import dcraft.struct.ListStruct;
import dcraft.struct.RecordStruct;
import dcraft.struct.Struct;
import dcraft.task.IWork;
import dcraft.task.StateWork;
import dcraft.task.StateWorkStep;
import dcraft.task.TaskContext;
import dcraft.tenant.Site;
import dcraft.tenant.Tenant;
import dcraft.tenant.TenantHub;

import java.time.ZonedDateTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

public class IndexSiteFilesWork extends StateWork {
	protected Deque<Vault> vaults = new ArrayDeque<>();
	protected Deque<String> folders = new ArrayDeque<>();
	
	protected Vault currentVault = null;
	
	protected StateWorkStep indexVault = null;
	protected StateWorkStep indexFromDeposits = null;
	protected StateWorkStep indexFolder = null;
	
	protected FileIndexAdapter adapter = null;
	
	@Override
	public void prepSteps(TaskContext trun) throws OperatingContextException {
		
		IConnectionManager connectionManager = ResourceHub.getResources().getDatabases().getDatabase();
		
		this.adapter = FileIndexAdapter.of(BasicRequestContext.of(connectionManager.allocateAdapter()));
		
		this
				.withStep(StateWorkStep.of("Prep Vaults List", this::prepVaults))
				.withStep(indexVault = StateWorkStep.of("Index Vault", this::doVault))
				.withStep(indexFromDeposits = StateWorkStep.of("Index From Deposits", this::doIndexFromDeposits))
				.withStep(indexFolder = StateWorkStep.of("Index Folder", this::doFolder));
	}
	
	public StateWorkStep prepVaults(TaskContext trun) throws OperatingContextException {
		this.adapter.clearSiteIndex(trun.getSite());
		
		for (Vault vault : trun.getSite().getVaults())
			this.vaults.addLast(vault);
		
		return this.indexVault;
	}
	
	public StateWorkStep doVault(TaskContext trun) throws OperatingContextException {
		Vault vault = this.vaults.pollFirst();
		
		if (vault == null)
			return StateWorkStep.STOP;
		
		this.currentVault = vault;
		this.folders.addLast("/");
		
		return this.indexFromDeposits;
	}
	
	// do this step before folder scan so we can get the most accurate history
	public StateWorkStep doIndexFromDeposits(TaskContext trun) throws OperatingContextException {
		// loop deposit index and rebuild from that
		
		try {
			List<Object> indexkeys = new ArrayList<>();
			
			indexkeys.add("root");
			indexkeys.add("dcDepositIndex");
			
			// start at top
			indexkeys.add(null);
			
			byte[] nodekey = this.adapter.getRequest().getInterface().nextPeerKey(indexkeys.toArray());
			
			while (nodekey != null) {
				Object nodeval = ByteUtil.extractValue(nodekey);
				
				indexkeys.remove(indexkeys.size() - 1);
				indexkeys.add(nodeval);
				
				// start at top
				indexkeys.add(null);
				
				byte[] depkey = this.adapter.getRequest().getInterface().nextPeerKey(indexkeys.toArray());
				
				while (depkey != null) {
					Object depval = ByteUtil.extractValue(depkey);
					
					indexkeys.remove(indexkeys.size() - 1);
					indexkeys.add(depval);
					
					Object manifestobj = this.adapter.getRequest().getInterface().get(indexkeys.toArray());
					
					RecordStruct manifest = Struct.objectToRecord(manifestobj);
					
					String mtenant = manifest.getFieldAsString("Tenant");
					String msite = manifest.getFieldAsString("Site");
					String mvault = manifest.getFieldAsString("Vault");
					
					if (trun.getTenant().getAlias().equals(mtenant) && trun.getSite().getAlias().equals(msite) && this.currentVault.getName().equals(mvault)) {
						ZonedDateTime timestamp = manifest.getFieldAsDateTime("TimeStamp");
						
						ListStruct write = manifest.getFieldAsList("Write");
						
						if (write != null) {
							for (int n = 0; n < write.size(); n++) {
								CommonPath path = CommonPath.from(write.getItemAsString(n));
								
								adapter.indexFile(
										this.currentVault,
										path,
										timestamp,
										RecordStruct.record()
												.with("Source", "Deposit")
												.with("Deposit", depval)
												.with("Op", "Write")
												.with("TimeStamp", timestamp)
												.with("Node", nodeval)
								);
							}
						}
						
						ListStruct delete = manifest.getFieldAsList("Delete");
						
						if (delete != null) {
							for (int n = 0; n < delete.size(); n++) {
								adapter.indexFile(
										this.currentVault,
										CommonPath.from(delete.getItemAsString(n)),
										timestamp,
										RecordStruct.record()
												.with("Source", "Deposit")
												.with("Deposit", depval)
												.with("Op", "Delete")
												.with("TimeStamp", timestamp)
												.with("Node", nodeval)
								);
							}
						}
					}
					
					depkey = this.adapter.getRequest().getInterface().nextPeerKey(indexkeys.toArray());
				}
				
				//get back to node level
				indexkeys.remove(indexkeys.size() - 1);
				
				nodekey = this.adapter.getRequest().getInterface().nextPeerKey(indexkeys.toArray());
			}
		}
		catch (DatabaseException x) {
			Logger.error("Unable to loop deposit index in db: " + x);
		}
		
		return this.indexFolder;
	}
	
	public StateWorkStep doFolder(TaskContext trun) throws OperatingContextException {
		OperationContext.getOrThrow().touch();
		
		IWork work = this.currentVault.buildIndexWork();
		
		if (work != null) {
			this.chainThen(trun, work, this.indexVault);
			return StateWorkStep.WAIT;
		}
		
		return this.indexVault;
	}
}
