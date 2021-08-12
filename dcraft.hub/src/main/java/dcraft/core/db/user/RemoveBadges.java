package dcraft.core.db.user;

import dcraft.db.Constants;
import dcraft.db.ICallContext;
import dcraft.db.proc.IStoredProc;
import dcraft.db.tables.TablesAdapter;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationOutcomeStruct;
import dcraft.struct.ListStruct;
import dcraft.struct.RecordStruct;

public class RemoveBadges implements IStoredProc {
	@Override
	public void execute(ICallContext request, OperationOutcomeStruct callback) throws OperatingContextException {
		RecordStruct data = request.getDataAsRecord();
		
		TablesAdapter db = TablesAdapter.of(request);
		
		ListStruct users = request.getDataAsRecord().getFieldAsList("Users");
		ListStruct tags = request.getDataAsRecord().getFieldAsList("Badges");

		for (int i = 0; i < users.size(); i++) {
			String uid = users.getItemAsString(i);
			
			for (int n = 0; n < tags.size(); n++) {
				String tag = tags.getItemAsString(n);
			
				if (uid.equals(Constants.DB_GLOBAL_ROOT_RECORD) && ("SysAdmin".equals(tag) || "Admin".equals(tag) || "Developer".equals(tag)))
					continue;
				
				db.retireList("dcUser", uid, "dcBadges", tag);
			}
		}

		callback.returnEmpty();
	}
}
