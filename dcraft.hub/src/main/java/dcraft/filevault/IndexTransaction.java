package dcraft.filevault;

import dcraft.hub.app.ApplicationHub;
import dcraft.hub.op.OperatingContextException;
import dcraft.struct.RecordStruct;
import dcraft.util.FileUtil;

import java.time.ZonedDateTime;

public class IndexTransaction extends TransactionBase {
	static public IndexTransaction of(Vault vault) {
		IndexTransaction tx = new IndexTransaction();
		tx.id = IndexTransaction.createTransactionId();
		tx.vault = vault;
		tx.nodeid = ApplicationHub.getNodeId();
		return tx;
	}
	
	/*
	 * if this returns successfully then the deposit is persisted and will be sync'ed unless the current node is
	 * completely destroyed before such sync can happen
	 */
	public void commit() throws OperatingContextException {
		this.vault.processTransaction(this);
	}
}
