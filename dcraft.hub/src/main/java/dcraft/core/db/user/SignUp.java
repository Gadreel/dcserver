package dcraft.core.db.user;

import dcraft.core.db.UserDataUtil;
import dcraft.db.ICallContext;
import dcraft.db.proc.IStoredProc;
import dcraft.db.proc.call.SignIn;
import dcraft.db.request.common.AddUserRequest;
import dcraft.db.request.update.DbRecordRequest;
import dcraft.db.tables.TableUtil;
import dcraft.db.tables.TablesAdapter;
import dcraft.hub.ResourceHub;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationOutcomeRecord;
import dcraft.hub.op.OperationOutcomeStruct;
import dcraft.interchange.google.RecaptchaUtil;
import dcraft.log.Logger;
import dcraft.struct.RecordStruct;
import dcraft.util.StringUtil;
import dcraft.xml.XElement;

public class SignUp implements IStoredProc {
	@Override
	public void execute(ICallContext request, OperationOutcomeStruct callback) throws OperatingContextException {
		RecordStruct data = request.getDataAsRecord();
		
		String captcha = data.getFieldAsString("Captcha");
		
		RecaptchaUtil.siteVerify(captcha, null, new OperationOutcomeRecord() {
			@Override
			public void callback(RecordStruct result) throws OperatingContextException {
				if (this.hasErrors()) {
					Logger.error("Unable to sign up - access not verified");
					
					callback.returnEmpty();
				} else {
					//System.out.println("Success");
					
					SignUp.this.submitWork(request, callback);
				}
			}
		});
	}
	
	public void submitWork(ICallContext request, OperationOutcomeStruct callback) throws OperatingContextException {
		XElement usersconfig = ResourceHub.getResources().getConfig().getTag("Users");

		if (usersconfig == null) {
			Logger.error("Users config missing");
			callback.returnEmpty();
			return;
		}

		if (! usersconfig.getAttributeAsBooleanOrFalse("SignUpEnabled")) {
			Logger.error("User sign up not enabled.");
			callback.returnEmpty();
			return;
		}

		RecordStruct data = request.getDataAsRecord();

		TablesAdapter db = TablesAdapter.ofNow(request);

		if (usersconfig.getAttributeAsBooleanOrFalse("SignUpConfirm")) {
			String tid = UserDataUtil.startConfirmAccount(db, data);

			callback.returnValue(RecordStruct.record()
					.with("Uuid", db.getStaticScalar("dcmThread", tid, "dcmUuid"))
			);
		}
		else {
			String password = data.getFieldAsString("Password");
			String uname = data.getFieldAsString("Username");
			String email = data.getFieldAsString("Email");

			if (StringUtil.isEmpty(uname))
				uname = email;

			DbRecordRequest userrequest = AddUserRequest.of(uname.toLowerCase())
					.withFirstName(data.getFieldAsString("FirstName"))
					.withLastName(data.getFieldAsString("LastName"))
					.withEmail(email)
					.withPhone(data.getFieldAsString("Phone"))
					.withPassword(password)
					.withConditionallyUpdateFields(data, "Address", "dcAddress", "Address2", "dcAddress2",
						"City", "dcCity", "State", "dcState", "Zip", "dcZip");

			String cid = TableUtil.updateRecord(db, userrequest);

			// sign the user in
			SignIn.signIn(request, db, cid, ! request.isReplicating());

			callback.returnValue(RecordStruct.record()
					.with("UserId", cid)
			);
		}
	}
}
