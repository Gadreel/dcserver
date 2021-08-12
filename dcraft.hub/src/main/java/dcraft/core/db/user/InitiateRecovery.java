package dcraft.core.db.user;

import dcraft.core.db.UserDataUtil;
import dcraft.db.ICallContext;
import dcraft.db.proc.IStoredProc;
import dcraft.db.tables.TablesAdapter;
import dcraft.hub.ResourceHub;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationOutcomeRecord;
import dcraft.hub.op.OperationOutcomeStruct;
import dcraft.interchange.google.RecaptchaUtil;
import dcraft.log.Logger;
import dcraft.struct.RecordStruct;
import dcraft.xml.XElement;

public class InitiateRecovery implements IStoredProc {
	@Override
	public void execute(ICallContext request, OperationOutcomeStruct callback) throws OperatingContextException {
		RecordStruct data = request.getDataAsRecord();

		XElement userconfig = ResourceHub.getResources().getConfig().getTag("Users");

		if ((userconfig != null) && userconfig.getAttributeAsBooleanOrFalse("RecoveryCaptcha")) {
			String captcha = data.getFieldAsString("Captcha");

			RecaptchaUtil.siteVerify(captcha, null, false, new OperationOutcomeRecord() {
				@Override
				public void callback(RecordStruct result) throws OperatingContextException {
					if (this.hasErrors()) {
						Logger.error("Unable to recover - access not verified");

						callback.returnEmpty();
					} else {
						//System.out.println("Success");

						InitiateRecovery.this.submitWork(request, callback);
					}
				}
			});
		}
		else {
			InitiateRecovery.this.submitWork(request, callback);
		}
	}
	
	public void submitWork(ICallContext request, OperationOutcomeStruct callback) throws OperatingContextException {
		XElement usersconfig = ResourceHub.getResources().getConfig().getTag("Users");

		if (usersconfig == null) {
			Logger.error("Users config missing");
			callback.returnEmpty();
			return;
		}

		if (! usersconfig.getAttributeAsBooleanOrFalse("RecoveryEnabled")) {
			Logger.error("User recovery not enabled.");
			callback.returnEmpty();
			return;
		}

		RecordStruct data = request.getDataAsRecord();

		TablesAdapter db = TablesAdapter.of(request);

		String tid = UserDataUtil.startRecoverAccount(db, data);

		callback.returnValue(RecordStruct.record()
				.with("Uuid", db.getScalar("dcmThread", tid, "dcmUuid"))
		);
	}
}
