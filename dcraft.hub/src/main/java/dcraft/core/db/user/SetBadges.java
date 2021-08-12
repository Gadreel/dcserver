package dcraft.core.db.user;

import dcraft.db.ICallContext;
import dcraft.db.proc.IStoredProc;
import dcraft.db.request.update.DbRecordRequest;
import dcraft.db.request.update.UpdateRecordRequest;
import dcraft.db.tables.TableUtil;
import dcraft.db.tables.TablesAdapter;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationOutcomeStruct;
import dcraft.struct.ListStruct;
import dcraft.struct.RecordStruct;

public class SetBadges implements IStoredProc {
	@Override
	public void execute(ICallContext request, OperationOutcomeStruct callback) throws OperatingContextException {
		RecordStruct data = request.getDataAsRecord();
		
		TablesAdapter db = TablesAdapter.of(request);
		
		ListStruct users = request.getDataAsRecord().getFieldAsList("Users");
		ListStruct tags = request.getDataAsRecord().getFieldAsList("Badges");

		for (int i = 0; i < users.size(); i++) {
			String uid = users.getItemAsString(i);
			
			DbRecordRequest req = UpdateRecordRequest.update()
					.withId(uid)
					.withTable("dcUser")
					.withSetList("dcBadges", tags);
			
			TableUtil.updateRecord(db, req);
		}

		callback.returnEmpty();
	}
}
