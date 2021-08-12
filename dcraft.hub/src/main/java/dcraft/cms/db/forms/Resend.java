package dcraft.cms.db.forms;

import dcraft.cms.thread.db.ThreadUtil;
import dcraft.db.ICallContext;
import dcraft.db.proc.IStoredProc;
import dcraft.db.tables.TablesAdapter;
import dcraft.filestore.CommonPath;
import dcraft.hub.app.ApplicationHub;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationOutcomeStruct;
import dcraft.log.Logger;
import dcraft.struct.RecordStruct;
import dcraft.struct.Struct;
import dcraft.task.Task;
import dcraft.task.TaskHub;
import dcraft.util.StringUtil;
import dcraft.xml.XElement;

public class Resend implements IStoredProc {
	@Override
	public void execute(ICallContext request, OperationOutcomeStruct callback) throws OperatingContextException {
		RecordStruct data = request.getDataAsRecord();

		TablesAdapter db = TablesAdapter.of(request);
		
		String id = ThreadUtil.getThreadId(db, data);

		String form = Struct.objectToString(db.getScalar("dcmThread", id, "dcmManagedFormName"));

		String scriptpath = null;

		XElement mform = ApplicationHub.getCatalogSettings("CMS-ManagedForm-" + form);

		if (mform == null) {
			String fid = Struct.objectToString(db.firstInIndex("dcmBasicCustomForm", "dcmAlias", form, true));

			if (StringUtil.isEmpty(fid)) {
				Logger.error("Managed form not enabled.");
				return;
			}

			scriptpath = "/dcm/forms/event-basic-form-submitted.dcs.xml";
		}
		else {
			scriptpath = mform.getAttribute("Script", "/dcm/forms/event-form-submitted.dcs.xml");
		}

		// TODO use task queue instead
		TaskHub.submit(Task.ofSubtask("ManagedForm submitted", "CMS")
				.withTopic("Batch")
				.withMaxTries(5)
				.withTimeout(10)        // TODO this should be graduated - 10 minutes moving up to 30 minutes if fails too many times
				.withParams(RecordStruct.record()
						.with("Id", id)
				)
				.withScript(CommonPath.from(scriptpath)));

		callback.returnEmpty();
	}
}
