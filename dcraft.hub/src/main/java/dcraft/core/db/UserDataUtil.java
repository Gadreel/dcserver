package dcraft.core.db;

import dcraft.cms.thread.db.ThreadUtil;
import dcraft.db.Constants;
import dcraft.db.request.common.AddUserRequest;
import dcraft.db.request.update.DbRecordRequest;
import dcraft.db.request.update.InsertRecordRequest;
import dcraft.db.request.update.UpdateRecordRequest;
import dcraft.db.tables.TableUtil;
import dcraft.db.tables.TablesAdapter;
import dcraft.filestore.CommonPath;
import dcraft.hub.ResourceHub;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
import dcraft.log.Logger;
import dcraft.struct.ListStruct;
import dcraft.struct.RecordStruct;
import dcraft.struct.Struct;
import dcraft.task.Task;
import dcraft.task.TaskHub;
import dcraft.util.StringUtil;
import dcraft.util.TimeUtil;
import dcraft.util.map.MapUtil;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

public class UserDataUtil {
	/*
		Programming Points
	 */
	
	static public void retireUserTrigger(TablesAdapter db, String id) throws OperatingContextException {
		List<String> badges = db.getStaticListKeys("dcUser", id, "dcBadges");
		
		db.setStaticScalar("dcUser", id, "dcBadgesRetired", ListStruct.list().withCollection(badges));
		
		for (String badge : badges) {
			db.retireStaticList("dcUser", id, "dcBadges", badge);
		}

		String uname = Struct.objectToString(db.getStaticScalar("dcUser", id, "dcUsername"));

		db.setStaticScalar("dcUser", id, "dcUsernameRetired", uname);
		db.setStaticScalar("dcUser", id, "dcUsername", id + "@user.retired");
	}
	
	static public void reviveUserTrigger(TablesAdapter db, String id) throws OperatingContextException {
		ListStruct oldbadges = Struct.objectToList(db.getStaticScalar("dcUser", id, "dcBadgesRetired"));
		
		if (oldbadges != null) {
			for (Struct badge : oldbadges.items()) {
				db.updateStaticList("dcUser", id, "dcBadges", badge.toString(), badge.toString());
			}
		}

		String uname = Struct.objectToString(db.getStaticScalar("dcUser", id, "dcUsernameRetired"));

		// tries but may fail
		db.setStaticScalar("dcUser", id, "dcUsername", uname);
	}
	
	/*
		Builders
	 */
	
	static public DbRecordRequest addUserWithConditions(RecordStruct data, boolean confirmed) throws OperatingContextException {
		String pword = data.getFieldAsString("Password");

		if (StringUtil.isNotEmpty(pword)) {
			pword = pword.trim();

			if (! AddUserRequest.meetsPasswordPolicy(pword, false)) {
				return null;
			}
		}

		String fpword = pword;

		String uname = data.getFieldAsString("Username");

		if (StringUtil.isEmpty(uname))
			uname = data.getFieldAsString("Email");

		if (StringUtil.isEmpty(uname) || "guest".equals(uname)) {
			Logger.errorTr(127);
			return null;
		}
		
		DbRecordRequest req = InsertRecordRequest.insert()
				.withTable("dcUser")
				.withUpdateField("dcUsername", uname.trim().toLowerCase())
				.withUpdateField("dcConfirmed", confirmed)
				.withConditionallySetFields(data, "FirstName", "dcFirstName",
				"LastName", "dcLastName", "Email", "dcEmail", "BackupEmail", "dcBackupEmail", "Phone", "dcPhone",
				"Description", "dcDescription", "Address", "dcAddress", "Address2", "dcAddress2", "City", "dcCity",
				"State", "dcState", "Zip", "dcZip", "Notices", "dcNotices", "DOB", "dcDOB", "DisplayName", "dcDisplayName",
				"Intro", "dcIntro", "ImageName", "dcImageName", "PrimaryLanguage", "dcPrimaryLanguage",
				"OtherLanguages", "dcOtherLanguages", "Gender", "dcGender", "Pronouns", "dcPronouns",
				"EthnicityNote", "dcEthnicityNote", "EducationMax", "dcEducationMax");
		
		//req.withUpdateField("dcCreated", TimeUtil.now());

		if (data.hasField("Badges"))
			req.withSetList("dcBadges", data.getFieldAsList("Badges"));
		
		if (data.hasField("Locale"))
			req.withSetList("dcLocale", data.getFieldAsList("Locale"));
		
		if (data.hasField("Chronology"))
			req.withSetList("dcChronology", data.getFieldAsList("Chronology"));
		
		if (data.hasField("Ethnicity"))
			req.withSetList("dcEthnicity", data.getFieldAsList("Ethnicity"));

		if (StringUtil.isNotEmpty(fpword)) {
			req.withUpdateField("dcPassword", OperationContext.getOrThrow().getUserContext().getTenant().getObfuscator().hashPassword(fpword));
		}

		return req;
	}
	
	static public DbRecordRequest updateUserWithConditions(RecordStruct data) throws OperatingContextException {
		String id = data.getFieldAsString("Id");
		
		String pword = data.getFieldAsString("Password");
		
		if (StringUtil.isNotEmpty(pword)) {
			pword = pword.trim();
			
			if (! AddUserRequest.meetsPasswordPolicy(pword, false)) {
				return null;
			}
		}
		
		String uname = data.getFieldAsString("Username");
		
		if ("guest".equals(uname)) {
			Logger.errorTr(127);
			return null;
		}
		
		// make sure root never changes uname
		if (id.equals(Constants.DB_GLOBAL_ROOT_RECORD)) {
			data.removeField("Username");
		}
		
		DbRecordRequest req = UpdateRecordRequest.update()
				.withTable("dcUser")
				.withId(id)
				.withConditionallyUpdateFields(data, "FirstName", "dcFirstName",
						"LastName", "dcLastName", "Email", "dcEmail", "BackupEmail", "dcBackupEmail", "Phone", "dcPhone",
						"Description", "dcDescription", "Address", "dcAddress", "Address2", "dcAddress2", "City", "dcCity",
						"State", "dcState", "Zip", "dcZip", "Notices", "dcNotices", "DOB", "dcDOB", "DisplayName", "dcDisplayName",
						"Intro", "dcIntro", "ImageName", "dcImageName", "PrimaryLanguage", "dcPrimaryLanguage",
						"OtherLanguages", "dcOtherLanguages", "Gender", "dcGender", "Pronouns", "dcPronouns",
						"EthnicityNote", "dcEthnicityNote", "EducationMax", "dcEducationMax");

		if (StringUtil.isNotEmpty(uname))
			req.withSetField("dcUsername", uname.trim().toLowerCase());

		if (data.hasField("Badges")) {
			ListStruct badges = data.getFieldAsList("Badges");
			
			// make sure root never loses admin/dev access
			if (id.equals(Constants.DB_GLOBAL_ROOT_RECORD)) {
				boolean fnddev = false;
				boolean fndsys = false;
				boolean fndadmin = false;
				
				for (Struct bs : badges.items()) {
					String badge = Struct.objectToString(bs);
					
					if ("Developer".equals(badge))
						fnddev = true;
					
					if ("SysAdmin".equals(badge))
						fndsys = true;
					
					if ("Admin".equals(badge))
						fndadmin = true;
				}
				
				if (! fnddev)
					badges.with("Developer");
				
				if (! fndsys)
					badges.with("SysAdmin");
				
				if (! fndadmin)
					badges.with("Admin");
			}
			// no sysadmin for ANYONE else
			else {
				for (Struct bs : badges.items()) {
					String badge = Struct.objectToString(bs);
					
					if ("SysAdmin".equals(badge)) {
						badges.removeItem(bs);
						break;
					}
				}
			}
			
			// if not root user and on shared hosting then don't allow developer badge
			if (! Constants.DB_GLOBAL_ROOT_RECORD.equals(OperationContext.getOrThrow().getUserContext().getUserId())) {
				if (Struct.objectToBooleanOrFalse(ResourceHub.getResources().getConfig().getAttribute("SharedHosting"))) {
					for (Struct bs : badges.items()) {
						String badge = Struct.objectToString(bs);
						
						if ("Developer".equals(badge)) {
							Logger.error("Developer badge not allowed on shored hosting.");
							return null;
						}
					}
				}
			}
			
			req.withSetList("dcBadges", badges);
		}
		
		// TODO update location if applicable
		
		if (data.hasField("Locale"))
			req.withSetList("dcLocale", data.getFieldAsList("Locale"));
		
		if (data.hasField("Chronology"))
			req.withSetList("dcChronology", data.getFieldAsList("Chronology"));
		
		if (data.hasField("Ethnicity"))
			req.withSetList("dcEthnicity", data.getFieldAsList("Ethnicity"));
		
		if (StringUtil.isNotEmpty(pword))
			req.withUpdateField("dcPassword", OperationContext.getOrThrow().getUserContext().getTenant().getObfuscator().hashPassword(pword));
		
		return req;
	}

	/*
		data should contain Email, Phone, FirstName and LastName
	 */
	static public String startConfirmAccount(TablesAdapter db, RecordStruct data) throws OperatingContextException {
		if (data.isFieldEmpty("Code")) {
			String code = StringUtil.buildSecurityCode(6);
			
			data.with("Code", code);
		}
		
		String title = "Sign Up: " + data.getFieldAsString("FirstName") + " " + data.getFieldAsString("LastName");

		String msg = "Email: " + data.getFieldAsString("Email") + "\n"
				+ "Phone: " + data.getFieldAsString("Phone") + "\n"
				+ "Code: " + data.getFieldAsString("Code") + "\n";

		if (data.isNotFieldEmpty("Title"))
			title = data.getFieldAsString("Title");

		if (data.isNotFieldEmpty("Message"))
			msg = data.getFieldAsString("Message");

		// don't deliver this yet, have user confirm first
		ZonedDateTime future = TimeUtil.now();   // LocalDate.of(3000, 1, 1).atStartOfDay(ZoneId.of("UTC"));

		String id = ThreadUtil.createThread(db, title,
				false, "ApproveUser", Constants.DB_GLOBAL_ROOT_RECORD, future, null);

		ThreadUtil.addContent(db, id, msg, "UnsafeMD");

		// message is good for 14 days
		db.setStaticScalar("dcmThread", id, "dcmExpireDate", TimeUtil.now().plusDays(14));

		db.setStaticScalar("dcmThread", id, "dcmSharedAttributes", data);

		// TODO configure pool and delivery date
		ThreadUtil.addParty(db, id, data.getFieldAsString("Party","/NoticesPool"), "/InBox", null);

		ThreadUtil.deliver(db, id, future);		// TODO make it so that thread is removed if user confirms
		
		Logger.info("Sending confirm code: " + data.getFieldAsString("Code"));
		
		// TODO use task queue instead
		TaskHub.submit(Task.ofSubtask("User confirm code trigger", "USER")
				.withTopic("Batch")
				.withMaxTries(5)
				.withTimeout(10)        // TODO this should be graduated - 10 minutes moving up to 30 minutes if fails too many times
				.withParams(RecordStruct.record()
						.with("Id", id)
				)
				.withScript(CommonPath.from("/dcw/user/event-user-confirm.dcs.xml")));

		return id;
	}

	/*
		data should contain Username
	 */
	static public String startRecoverAccount(TablesAdapter db, RecordStruct data) throws OperatingContextException {
		String userid = Struct.objectToString(db.firstInIndex("dcUser", "dcUsername", data.getFieldAsString("Username").trim().toLowerCase(), false));

		if (userid == null) {
			Logger.error("Recovery failed.");
			return null;
		}

		data.with("UserId", userid);
		data.with("FirstName", db.getStaticScalar("dcUser", userid, "dcFirstName"));
		data.with("LastName", db.getStaticScalar("dcUser", userid, "dcLastName"));
		data.with("Email", db.getStaticScalar("dcUser", userid, "dcEmail"));
		data.with("Phone", db.getStaticScalar("dcUser", userid, "dcPhone"));

		String title = "Recover: " + data.getFieldAsString("FirstName") + " " + data.getFieldAsString("LastName");

		String msg = "Email: " + data.getFieldAsString("Email") + "\n"
				+ "Phone: " + data.getFieldAsString("Phone") + "\n";

		if (data.isNotFieldEmpty("Title"))
			title = data.getFieldAsString("Title");

		if (data.isNotFieldEmpty("Message"))
			msg = data.getFieldAsString("Message");

		String code = StringUtil.buildSecurityCode(6);

		data.with("Code", code);

		// don't deliver this yet, have user confirm first
		ZonedDateTime future = TimeUtil.now();   // LocalDate.of(3000, 1, 1).atStartOfDay(ZoneId.of("UTC"));

		String id = ThreadUtil.createThread(db, title,
				false, "RecoverUser", Constants.DB_GLOBAL_ROOT_RECORD, future, null);

		ThreadUtil.addContent(db, id, msg, "UnsafeMD");

		// message is good for 14 days
		db.setStaticScalar("dcmThread", id, "dcmExpireDate", TimeUtil.now().plusDays(14));

		db.setStaticScalar("dcmThread", id, "dcmSharedAttributes", data);

		// TODO configure pool and delivery date
		ThreadUtil.addParty(db, id, data.getFieldAsString("Party","/NoticesPool"), "/InBox", null);

		ThreadUtil.deliver(db, id, future);		// TODO make it so that thread is removed if user confirms

		// TODO use task queue instead
		TaskHub.submit(Task.ofSubtask("User confirm code trigger", "USER")
				.withTopic("Batch")
				.withMaxTries(5)
				.withTimeout(10)        // TODO this should be graduated - 10 minutes moving up to 30 minutes if fails too many times
				.withParams(RecordStruct.record()
						.with("Id", id)
				)
				.withScript(CommonPath.from("/dcw/user/event-user-recover.dcs.xml")));

		return id;
	}
	
	static public void updateUserLocation(TablesAdapter db, String id) throws OperatingContextException {
		if (! db.isCurrent("dcUser", id) || ! MapUtil.geocodeEnabled())
			return;
		
		String address = Struct.objectToString(db.getStaticScalar("dcUser", id, "dcAddress"));
		String city = Struct.objectToString(db.getStaticScalar("dcUser", id, "dcCity"));
		String state = Struct.objectToString(db.getStaticScalar("dcUser", id, "dcState"));
		String zip = Struct.objectToString(db.getStaticScalar("dcUser", id, "dcZip"));

		if (StringUtil.isEmpty(address) || StringUtil.isEmpty(city) || StringUtil.isEmpty(state) || StringUtil.isEmpty(zip)) {
			db.retireStaticScalar("dcUser", id, "dcLocation");
		}
		else {
			String loc = MapUtil.getLatLong(address, city, state, zip);
			
			if (loc != null)
				db.setStaticScalar("dcUser", id, "dcLocation", loc);
			else
				db.retireStaticScalar("dcUser", id, "dcLocation");
		}
	}
}
