package dcraft.cms.db.forms;

import dcraft.cms.thread.db.ThreadUtil;
import dcraft.db.ICallContext;
import dcraft.db.proc.IStoredProc;
import dcraft.db.tables.TablesAdapter;
import dcraft.filevault.Transaction;
import dcraft.filevault.Vault;
import dcraft.filevault.VaultUtil;
import dcraft.hub.app.ApplicationHub;
import dcraft.hub.op.*;
import dcraft.log.Logger;
import dcraft.struct.RecordStruct;
import dcraft.struct.Struct;
import dcraft.util.StringUtil;
import dcraft.util.TimeUtil;
import dcraft.xml.XElement;

import java.time.ZonedDateTime;

public class Complete implements IStoredProc {
	@Override
	public void execute(ICallContext request, OperationOutcomeStruct callback) throws OperatingContextException {
		RecordStruct data = request.getDataAsRecord();

		String form = data.getFieldAsString("Form");
		
		TablesAdapter db = TablesAdapter.of(request);
		
		String id = ThreadUtil.getThreadId(db, data);
		
		Object sfrm = db.getScalar("dcmThread", id, "dcmManagedFormName");
		
		if (! form.equals(sfrm)) {
			Logger.error("Incorrect form name.");
			callback.returnEmpty();
			return;
		}
		
		ZonedDateTime now = TimeUtil.now();
		ZonedDateTime delivered = Struct.objectToDateTime(db.getScalar("dcmThread", id, "dcmModified"));
		
		if (delivered.isBefore(now)) {
			Logger.error("Form already completed.");
			callback.returnEmpty();
			return;
		}
		
		XElement mform = ApplicationHub.getCatalogSettings("CMS-ManagedForm-" + form);
		
		if (mform == null) {
			String fid = Struct.objectToString(db.firstInIndex("dcmBasicCustomForm", "dcmAlias", form, true));
			
			if (StringUtil.isEmpty(fid)) {
				Logger.error("Managed form not enabled.");
				callback.returnEmpty();
				return;
			}
			
			String emails = Struct.objectToString(db.getScalar("dcmBasicCustomForm", fid, "dcmEmail"));
			
			db.updateScalar("dcmThread", id,"dcmManagedFormEmail", emails);
			db.updateScalar("dcmThread", id,"dcmManagedFormBasic", fid);
		}

		ThreadUtil.updateDeliver(db, id, now);

		// finish deposit
		Vault vault = OperationContext.getOrThrow().getSite().getVault("ManagedForms");

		if (vault != null) {
			String token = data.getFieldAsString("Token");

			String txid = VaultUtil.getSessionTokenTx(token);

			Transaction tx = vault.buildUpdateTransaction(txid, null);

			tx.commitTransaction(new OperationOutcomeEmpty() {
				@Override
				public void callback() throws OperatingContextException {
					vault.clearToken(data);

					callback.returnEmpty();
				}
			});
		}
		else {
			callback.returnEmpty();
		}
	}
}
