package dcraft.core.db.user;

import dcraft.cms.thread.db.ThreadUtil;
import dcraft.db.Constants;
import dcraft.db.ICallContext;
import dcraft.db.proc.IStoredProc;
import dcraft.db.proc.call.SignIn;
import dcraft.db.request.common.AddUserRequest;
import dcraft.db.request.update.DbRecordRequest;
import dcraft.db.tables.TableUtil;
import dcraft.db.tables.TablesAdapter;
import dcraft.filestore.CommonPath;
import dcraft.hub.ResourceHub;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationOutcomeRecord;
import dcraft.hub.op.OperationOutcomeStruct;
import dcraft.interchange.google.RecaptchaUtil;
import dcraft.log.Logger;
import dcraft.struct.RecordStruct;
import dcraft.task.Task;
import dcraft.task.TaskHub;
import dcraft.util.StringUtil;
import dcraft.util.TimeUtil;
import dcraft.xml.XElement;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;

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
			String msg = "Email: " + data.getFieldAsString("Email") + "\n"
					+ "Phone: " + data.getFieldAsString("Phone") + "\n";

			String code = StringUtil.buildSecurityCode(6);

			data.with("Code", code);

			// don't deliver this yet, have user confirm first
			ZonedDateTime future = LocalDate.of(3000, 1, 1).atStartOfDay(ZoneId.of("UTC"));

			String id = ThreadUtil.createThread(db,
					"Sign Up: " + data.getFieldAsString("FirstName") + " " + data.getFieldAsString("LastName"),
					false, "ApproveUser", Constants.DB_GLOBAL_ROOT_RECORD, future, null);

			ThreadUtil.addContent(db, id, msg, "UnsafeMD");

			// message is good for 14 days
			db.setStaticScalar("dcmThread", id, "dcmExpireDate", TimeUtil.now().plusDays(14));

			db.setStaticScalar("dcmThread", id, "dcmSharedAttributes", data);

			// TODO configure pool and delivery date
			ThreadUtil.addParty(db, id, "/NoticesPool", "/InBox", null);

			ThreadUtil.deliver(db, id, future);		// TODO make it so that thread is removed if user confirms

			// TODO use task queue instead
			TaskHub.submit(Task.ofSubtask("User confirm code trigger", "USER")
					.withTopic("Batch")
					.withMaxTries(5)
					.withTimeout(10)        // TODO this should be graduated - 10 minutes moving up to 30 minutes if fails too many times
					.withParams(RecordStruct.record()
							.with("Id", id)
					)
					.withScript(CommonPath.from("/dcw/user/event-user-confirm.dcs.xml")));

			callback.returnValue(RecordStruct.record()
					.with("Uuid", db.getStaticScalar("dcmThread", id, "dcmUuid"))
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
