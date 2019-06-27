package dcraft.cms.dashboard.db;

import dcraft.db.ICallContext;
import dcraft.db.proc.IStoredProc;
import dcraft.hub.app.ApplicationHub;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationOutcomeStruct;
import dcraft.log.Logger;
import dcraft.struct.RecordStruct;
import dcraft.task.Task;
import dcraft.task.TaskContext;
import dcraft.task.TaskHub;
import dcraft.task.TaskObserver;
import dcraft.tool.certs.CertUtil;
import dcraft.tool.certs.RenewSiteManualWork;
import org.shredzone.acme4j.Account;

import java.util.List;

public class EnableCertService implements IStoredProc {
	@Override
	public void execute(ICallContext request, OperationOutcomeStruct callback) throws OperatingContextException {
		if (ApplicationHub.isProduction()) {
			Logger.error("Enable Cert Service not allowed on production, yet.");
			// TODO enable on production - save keys in Deposits so it copies to other hubs
			callback.returnEmpty();
			return;
		}
		
		RecordStruct data = request.getDataAsRecord();
		
		if (! "LE".equals(data.getFieldAsString("Service"))) {
			Logger.error("Service not supported");
			callback.returnEmpty();
			return;
		}
		
		try {
			Account account = CertUtil.findOrRegisterAccount(CertUtil.newSession(true), CertUtil.loadOrCreateUserKeyPair(), null);
			
			if (account == null) {
				Logger.error("Unable to register service account.");
			}
		}
		catch (Exception x) {
			Logger.error("Unable to create account keys.");
		}

		callback.returnEmpty();
	}
}
