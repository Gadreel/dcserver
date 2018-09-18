package dcraft.core.db;

import dcraft.db.Constants;
import dcraft.db.request.common.AddUserRequest;
import dcraft.db.request.update.DbRecordRequest;
import dcraft.db.request.update.InsertRecordRequest;
import dcraft.db.request.update.UpdateRecordRequest;
import dcraft.db.tables.TableUtil;
import dcraft.db.tables.TablesAdapter;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
import dcraft.log.Logger;
import dcraft.struct.ListStruct;
import dcraft.struct.RecordStruct;
import dcraft.struct.Struct;
import dcraft.util.StringUtil;
import dcraft.util.TimeUtil;

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
	}
	
	static public void reviveUserTrigger(TablesAdapter db, String id) throws OperatingContextException {
		ListStruct oldbadges = Struct.objectToList(db.getStaticScalar("dcUser", id, "dcBadgesRetired"));
		
		if (oldbadges != null) {
			for (Struct badge : oldbadges.items()) {
				db.updateStaticList("dcUser", id, "dcBadges", badge.toString(), badge.toString());
			}
		}
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
				.withUpdateField("dcUsername", uname.toLowerCase())
				.withUpdateField("dcConfirmed", confirmed)
				.withConditionallySetFields(data, "FirstName", "dcFirstName",
				"LastName", "dcLastName", "Email", "dcEmail", "BackupEmail", "dcBackupEmail", "Phone", "dcPhone",
				"Description", "dcDescription", "Address", "dcAddress", "Address2", "dcAddress2", "City", "dcCity",
				"State", "dcState", "Zip", "dcZip", "Notices", "dcNotices");
		
		//req.withUpdateField("dcCreated", TimeUtil.now());

		if (data.hasField("Badges"))
			req.withSetList("dcBadges", data.getFieldAsList("Badges"));
		
		if (data.hasField("Locale"))
			req.withSetList("dcLocale", data.getFieldAsList("Locale"));
		
		if (data.hasField("Chronology"))
			req.withSetList("dcChronology", data.getFieldAsList("Chronology"));

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
						"State", "dcState", "Zip", "dcZip", "Notices", "dcNotices");

		if (StringUtil.isNotEmpty(uname))
			req.withSetField("dcUsername", uname.toLowerCase());

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
			
			req.withSetList("dcBadges", badges);
		}
		
		if (data.hasField("Locale"))
			req.withSetList("dcLocale", data.getFieldAsList("Locale"));
		
		if (data.hasField("Chronology"))
			req.withSetList("dcChronology", data.getFieldAsList("Chronology"));
		
		if (StringUtil.isNotEmpty(pword))
			req.withUpdateField("dcPassword", OperationContext.getOrThrow().getUserContext().getTenant().getObfuscator().hashPassword(pword));
		
		return req;
	}
}
