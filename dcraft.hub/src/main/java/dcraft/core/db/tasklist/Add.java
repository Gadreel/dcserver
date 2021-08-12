package dcraft.core.db.tasklist;

import dcraft.db.ICallContext;
import dcraft.db.proc.IStoredProc;
import dcraft.db.tables.TablesAdapter;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationOutcomeStruct;
import dcraft.struct.ListStruct;
import dcraft.struct.RecordStruct;
import dcraft.struct.Struct;

public class Add implements IStoredProc {
	@Override
	public void execute(ICallContext request, OperationOutcomeStruct callback) throws OperatingContextException {
		RecordStruct data = request.getDataAsRecord();
		TablesAdapter db = TablesAdapter.of(request);

		String newid = TaskListUtil.addTaskListRecord(db, data);

		ListStruct tabs = ListStruct.list();

		RecordStruct lead = Struct.objectToRecord(db.getScalar("dcTaskList", newid, "dcLeadTabOption"));

		if (lead != null)
			tabs.with(lead);

		for (String step : db.getListKeys("dcTaskList", newid, "dcStepTask")) {
			String stepid = Struct.objectToString(db.getList("dcTaskList", newid, "dcStepTask", step));

			RecordStruct child = Struct.objectToRecord(db.getScalar("dcTaskList", stepid, "dcChildTabOption"));

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
