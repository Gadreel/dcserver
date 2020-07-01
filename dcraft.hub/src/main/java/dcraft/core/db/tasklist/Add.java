package dcraft.core.db.tasklist;

import dcraft.core.db.UserDataUtil;
import dcraft.db.ICallContext;
import dcraft.db.proc.IStoredProc;
import dcraft.db.request.update.DbRecordRequest;
import dcraft.db.tables.TableUtil;
import dcraft.db.tables.TablesAdapter;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationOutcomeStruct;
import dcraft.struct.ListStruct;
import dcraft.struct.RecordStruct;
import dcraft.struct.Struct;
import dcraft.util.StringUtil;

import java.util.List;

public class Add implements IStoredProc {
	@Override
	public void execute(ICallContext request, OperationOutcomeStruct callback) throws OperatingContextException {
		RecordStruct data = request.getDataAsRecord();
		TablesAdapter db = TablesAdapter.ofNow(request);

		String newid = TaskListUtil.addTaskListRecord(db, data);

		ListStruct tabs = ListStruct.list();

		RecordStruct lead = Struct.objectToRecord(db.getStaticScalar("dcTaskList", newid, "dcLeadTabOption"));

		if (lead != null)
			tabs.with(lead);

		for (String step : db.getStaticListKeys("dcTaskList", newid, "dcStepTask")) {
			String stepid = Struct.objectToString(db.getStaticList("dcTaskList", newid, "dcStepTask", step));

			RecordStruct child = Struct.objectToRecord(db.getStaticScalar("dcTaskList", stepid, "dcChildTabOption"));

			if (child != null)
				tabs.with(child);
		}

		callback.returnValue(
				RecordStruct.record()
					.with("Id", newid)
					.with("Tabs", tabs)
		);
	}
}
