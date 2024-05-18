package dcraft.db.proc.trigger;

import dcraft.core.db.UserDataUtil;
import dcraft.db.proc.ITrigger;
import dcraft.db.proc.call.SignIn;
import dcraft.db.tables.TablesAdapter;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
import dcraft.mail.CommUtil;
import dcraft.mail.MailUtil;
import dcraft.struct.BaseStruct;
import dcraft.struct.FieldStruct;
import dcraft.struct.RecordStruct;
import dcraft.struct.Struct;
import dcraft.util.StringUtil;
import dcraft.util.TimeUtil;

import java.time.ZonedDateTime;

public class AfterUserUpdate implements ITrigger {
	@Override
	public boolean execute(TablesAdapter db, String table, String id, BaseStruct context) throws OperatingContextException {
		if ("dcUser".equals(table)) {
			// should be a record
			if (context instanceof RecordStruct) {
				RecordStruct fields = (RecordStruct) context;
				boolean locupdate = false;

				for (FieldStruct field : fields.getFields()) {
					String fname = field.getName();

					// for any change, record once
					if ("dcAddress".equals(fname) || "dcCity".equals(fname) || "dcState".equals(fname) || "dcZip".equals(fname)) {
						locupdate = true;
					}

					if ("dcZip".equals(fname)) {
						// set zip prefix if possible
						String zip = Struct.objectToString(db.getScalar(table, id, "dcZip"));

						if (StringUtil.isNotEmpty(zip))
							db.updateScalar(table, id, "dcZipPrefix", zip.substring(0, 3));
					}
					else if ("dcFirstName".equals(fname)) {
						// set display name if possible
						String displayName = Struct.objectToString(db.getScalar(table, id, "dcDisplayName"));
						
						if (StringUtil.isEmpty(displayName)) {
							String firstName = Struct.objectToString(db.getScalar(table, id, "dcFirstName"));
							
							if (StringUtil.isNotEmpty(firstName)) {
								db.updateScalar(table, id, "dcDisplayName", firstName);
							}
						}
					}
					else if ("dcEmail".equals(fname)) {
						String newemail = Struct.objectToString(db.getScalar(table, id, "dcEmail"));

						if (StringUtil.isNotEmpty(newemail)) {
							String trackid = CommUtil.ensureCommTrack("email", newemail, id);

							String currentEmailTracker = Struct.objectToString(db.getScalar(table, id, "dccCurrentEmailTracker"));

							// list it as current
							if (! trackid.equals(currentEmailTracker))
								db.updateScalar(table, id, "dccCurrentEmailTracker", trackid);
						}
					}
				}

				if (locupdate) {
					UserDataUtil.updateUserLocation(db, id);
				}
			}
		}

		if (id.equals(OperationContext.getOrThrow().getUserContext().getUserId())) {
			// get proper context in
			SignIn.updateContext(db, id);
		}

		return true;
	}
}
