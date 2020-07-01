package dcraft.core.db.admin;

import dcraft.db.ICallContext;
import dcraft.db.proc.IStoredProc;
import dcraft.db.request.update.DbRecordRequest;
import dcraft.db.request.update.InsertRecordRequest;
import dcraft.db.tables.TableUtil;
import dcraft.db.tables.TablesAdapter;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationOutcomeStruct;
import dcraft.struct.ListStruct;
import dcraft.struct.RecordStruct;
import dcraft.task.Task;
import dcraft.task.TaskHub;
import dcraft.tool.backup.BackupWork;
import dcraft.util.TimeUtil;

public class GoLiveReport implements IStoredProc {
	@Override
	public void execute(ICallContext request, OperationOutcomeStruct callback) throws OperatingContextException {
		TablesAdapter db = TablesAdapter.ofNow(request);

		RecordStruct s0 = RecordStruct.record()
				.with("Alias", "Step0")
				.with("Title", "Step 0")
				.with("Path", "/dcr/test-task-list/step-0");

		DbRecordRequest step0 = InsertRecordRequest.insert()
				.withTable("dcTaskList")
				.withUpdateField("dcTitle", "step 0 tab")
				.withUpdateField("dcDescription", "step 0 description")
				.withUpdateField("dcChildTabOption", s0)
				.withUpdateField("dcWeight", 40);

		String id0 = TableUtil.updateRecord(db, step0);

		RecordStruct s1 = RecordStruct.record()
				.with("Alias", "Step1")
				.with("Title", "Step 1")
				.with("Path", "/dcr/test-task-list/step-1");

		DbRecordRequest step1 = InsertRecordRequest.insert()
				.withTable("dcTaskList")
				.withUpdateField("dcTitle", "step 1 tab")
				.withUpdateField("dcDescription", "step 1 description")
				.withUpdateField("dcChildTabOption", s1)
				.withUpdateField("dcWeight", 20);

		String id1 = TableUtil.updateRecord(db, step1);

		RecordStruct s2 = RecordStruct.record()
				.with("Alias", "Step2")
				.with("Title", "Step 2")
				.with("Path", "/dcr/test-task-list/step-2");

		DbRecordRequest step2 = InsertRecordRequest.insert()
				.withTable("dcTaskList")
				.withUpdateField("dcTitle", "step 2 tab")
				.withUpdateField("dcDescription", "step 2 description")
				.withUpdateField("dcChildTabOption", s2)
				.withUpdateField("dcWeight", 40);

		String id2 = TableUtil.updateRecord(db, step2);

		RecordStruct m = RecordStruct.record()
				.with("Alias", "Report")
				.with("Title", "Report Summary")
				.with("Path", "/dcr/test-task-list/main");

		DbRecordRequest main = InsertRecordRequest.insert()
				.withTable("dcTaskList")
				.withUpdateField("dcTitle", "main tab")
				.withUpdateField("dcDescription", "main description")
				.withUpdateField("dcExpire", TimeUtil.now().plusMonths(3))
				.withUpdateField("dcStartedAt", TimeUtil.now())
				.withUpdateField("dcParams", RecordStruct.record()
						.with("A", 123)
						.with("B", "echo")
				)
				.withUpdateField("dcLeadTabOption", m)
				.withUpdateField("dcStepTask", "0000", id0)
				.withUpdateField("dcStepTask", "0001", id1)
				.withUpdateField("dcStepTask", "0002", id2);

		String id = TableUtil.updateRecord(db, main);

		callback.returnValue(RecordStruct.record()
				.with("Title", "go live task")
				.with("Description", "go live task description")
				.with("Params", RecordStruct.record()
						.with("Id", id)
				)
				.with("Options", ListStruct.list(m , s0, s1, s2))
		);
	}
}
