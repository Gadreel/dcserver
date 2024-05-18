package dcraft.db.proc.trigger;

import dcraft.db.proc.ITrigger;
import dcraft.db.tables.TablesAdapter;
import dcraft.hub.op.OperatingContextException;
import dcraft.mail.MailUtil;
import dcraft.struct.BaseStruct;
import dcraft.struct.FieldStruct;
import dcraft.struct.RecordStruct;
import dcraft.struct.Struct;
import dcraft.util.StringUtil;

public class BeforeUserInsert implements ITrigger {
	@Override
	public boolean execute(TablesAdapter db, String table, String id, BaseStruct context) throws OperatingContextException {
		if ("dcUser".equals(table)) {
			// should be a record
					/*
			if (context instanceof RecordStruct) {
				RecordStruct fields = (RecordStruct) context;

				for (FieldStruct field : fields.getFields()) {
					String fname = field.getName();

					if ("dcEmail".equals(fname)) {
						String email = Struct.objectToString(db.getScalar(table, id, "dcEmail"));

						if (StringUtil.isNotEmpty(email)) {
							String oldIndexable = MailUtil.indexableEmailAddress(email);
							String newIndexable = MailUtil.indexableEmailAddress(Struct.objectToRecord(field.getValue()).getFieldAsString("Data"));

							System.out.println("compare email: " + oldIndexable + " : " + newIndexable);
						}
					}
				}
			}
					 */
		}

		return true;
	}
}
