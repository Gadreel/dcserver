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

public class BeforeUserUpdate implements ITrigger {
	@Override
	public boolean execute(TablesAdapter db, String table, String id, BaseStruct context) throws OperatingContextException {
		if ("dcUser".equals(table)) {
			// should be a record
			if (context instanceof RecordStruct) {
				RecordStruct fields = (RecordStruct) context;

				for (FieldStruct field : fields.getFields()) {
					String fname = field.getName();

					if ("dcEmail".equals(fname)) {
						String oldemail = Struct.objectToString(db.getScalar(table, id, "dcEmail"));
						String newemail = Struct.objectToRecord(field.getValue()).getFieldAsString("Data");

						if (StringUtil.isNotEmpty(oldemail) && StringUtil.isNotEmpty(newemail)) {
							String oldIndexable = MailUtil.indexableEmailAddress(oldemail);
							String newIndexable = MailUtil.indexableEmailAddress(newemail);

							// indexable is for compare only - this is considered a unique form of the address
							if (! newIndexable.equals(oldIndexable)) {
								System.out.println("email history: " + oldIndexable + " : " + newIndexable);

								// lookup the comm tracker for the old email, create if needed

								String trackid = CommUtil.ensureCommTrack("email", oldemail, id);

								// list it as historical

								ZonedDateTime stamp = TimeUtil.now();
								String now = TimeUtil.stampFmt.format(stamp);

								db.updateList(table, id, "dccCommHistoryStamp", now, now);
								db.updateList(table, id, "dccCommHistoryId", now, trackid);

								// don't retire dccCurrentEmailTracker here, though that would be natural let's keep it until AfterUpdate
							}
						}
					}
				}
			}
		}

		return true;
	}
}
