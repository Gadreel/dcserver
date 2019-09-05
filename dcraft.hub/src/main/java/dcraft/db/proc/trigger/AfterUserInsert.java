package dcraft.db.proc.trigger;

import dcraft.core.db.UserDataUtil;
import dcraft.db.proc.ITrigger;
import dcraft.db.proc.call.SignIn;
import dcraft.db.tables.TablesAdapter;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
import dcraft.struct.FieldStruct;
import dcraft.struct.RecordStruct;
import dcraft.struct.Struct;
import dcraft.util.StringUtil;

public class AfterUserInsert implements ITrigger {
	@Override
	public boolean execute(TablesAdapter db, String table, String id, Struct context) throws OperatingContextException {
		if ("dcUser".equals(table)) {
			// should be a record
			if (context instanceof RecordStruct) {
				RecordStruct fields = (RecordStruct) context;
				boolean locupdate = false;
				
				for (FieldStruct field : fields.getFields()) {
					String fname = field.getName();
					
					// for any change, record once
					if ("dcAddress".equals(fname) || "dcCity".equals(fname) || "dcState".equals(fname) || "dcZip".equals(fname)) {
						if (! locupdate) {
							UserDataUtil.updateUserLocation(db, id);
							locupdate = true;
						}
					}

					if ("dcZip".equals(fname)) {
						// set zip prefix if possible
						String zip = Struct.objectToString(db.getStaticScalar(table, id, "dcZip"));

						if (StringUtil.isNotEmpty(zip))
							db.updateStaticScalar(table, id, "dcZipPrefix", zip.substring(0, 3));
					}
					else if ("dcFirstName".equals(fname)) {
						// set display name if possible
						String displayName = Struct.objectToString(db.getStaticScalar(table, id, "dcDisplayName"));
						
						if (StringUtil.isEmpty(displayName)) {
							String firstName = Struct.objectToString(db.getStaticScalar(table, id, "dcFirstName"));
							
							if (StringUtil.isNotEmpty(firstName)) {
								db.updateStaticScalar(table, id, "dcDisplayName", firstName);
							}
						}
					}
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
