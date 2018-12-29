package dcraft.filevault;

import dcraft.db.DatabaseAdapter;
import dcraft.db.DatabaseException;
import dcraft.db.IConnectionManager;
import dcraft.filestore.CommonPath;
import dcraft.hub.ResourceHub;
import dcraft.hub.op.OperatingContextException;
import dcraft.log.Logger;
import dcraft.struct.ListStruct;
import dcraft.struct.RecordStruct;
import dcraft.util.FileUtil;
import dcraft.util.StringUtil;

public class ManifestTransaction extends TransactionBase {
	static public ManifestTransaction of(Vault vault, RecordStruct manifest, String depositid, String nodeid) {
		ManifestTransaction tx = new ManifestTransaction();
		tx.id = ManifestTransaction.createTransactionId();
		tx.vault = vault;
		tx.manifest = manifest;
		tx.nodeid = nodeid;
		tx.depositid = depositid;
		
		tx.loadManifest();
		
		return tx;
	}
	
	protected RecordStruct manifest = null;
	
	protected void loadManifest() {
		ListStruct deletes = this.manifest.getFieldAsList("Delete");
		
		if (deletes != null) {
			for (int i = 0; i < deletes.size(); i++) {
				this.deletelist.add(CommonPath.from(deletes.getItemAsString(i)));
			}
		}
		
		String cleanup = this.manifest.getFieldAsString("Clean");
		
		if (StringUtil.isNotEmpty(cleanup))
			this.cleanfolder = CommonPath.from(cleanup);
		
		ListStruct writes = this.manifest.getFieldAsList("Write");
		
		if (writes != null) {
			for (int i = 0; i < writes.size(); i++) {
				this.updatelist.add(CommonPath.from(writes.getItemAsString(i)));
			}
		}
		
		this.timestamp = this.manifest.getFieldAsDateTime("TimeStamp");
	}
	
	public RecordStruct getManifest() {
		return this.manifest;
	}
	
	/*
	 * if this returns successfully then the deposit is persisted and will be sync'ed unless the current node is
	 * completely destroyed before such sync can happen
	 */
	public void commit() throws OperatingContextException {
		this.vault.beforeSubmitTransaction(this);

		// delete the expanded temp files remaining, if any, and the folder
		FileUtil.deleteDirectory(this.getFolder().getPath());
		
		this.vault.processTransaction(this);
		
		// index the deposit if local database
		if (ResourceHub.getResources().getDatabases().hasDefaultDatabase()) {
			IConnectionManager connectionManager = ResourceHub.getResources().getDatabases().getDatabase();
			
			DatabaseAdapter adapter = connectionManager.allocateAdapter();
			
			try {
				adapter.set("root", "dcDepositIndex", this.getNodeId(), this.getDepositId(), this.manifest);
			}
			catch (DatabaseException x) {
				Logger.error("Unable to index deposit in db: " + x);
			}
		}
		
	}
	
	public void rollback() throws OperatingContextException {
		// delete the expanded temp files
		FileUtil.deleteDirectory(this.getFolder().getPath());
	}
}
