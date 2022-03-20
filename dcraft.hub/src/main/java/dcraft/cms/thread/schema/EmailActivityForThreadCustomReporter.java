package dcraft.cms.thread.schema;

import dcraft.cms.thread.db.email.IEmailActivityForThreadCustomReporter;
import dcraft.cms.thread.work.EmailActivityThreadCustomWork;
import dcraft.db.request.update.UpdateRecordRequest;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
import dcraft.hub.op.OperationOutcomeStruct;
import dcraft.hub.op.UserContext;
import dcraft.script.StackUtil;
import dcraft.script.work.ReturnOption;
import dcraft.script.work.StackWork;
import dcraft.service.ServiceHub;
import dcraft.service.ServiceRequest;
import dcraft.struct.BaseStruct;
import dcraft.struct.RecordStruct;
import dcraft.struct.Struct;
import dcraft.task.IWork;
import dcraft.task.Task;
import dcraft.task.TaskHub;
import dcraft.xml.XElement;

public class EmailActivityForThreadCustomReporter extends RecordStruct implements IEmailActivityForThreadCustomReporter {
	@Override
	public boolean reportReceived(String actid, String auditkey, RecordStruct reportData, RecordStruct handlerData) throws OperatingContextException {
		System.out.println("EmailActivityForThreadCustomReporter got report: " + reportData.toPrettyString() + " related to: " + handlerData.toPrettyString());

		TaskHub.submit(Task.ofHubRootSameSite()
				.withTitle("test email activity report")
				.withParams(RecordStruct.record()
						.with("ActivityId", actid)
						.with("AuditKey", auditkey)
				)
				.withWork(EmailActivityThreadCustomWork.of(actid, auditkey))
		);

		return true;
	}

	@Override
	public ReturnOption operation(StackWork state, XElement code) throws OperatingContextException {
		if ("ReportReceived".equals(code.getName())) {
			// TODO review and revise, maybe get some info from the dcmOutboundEmailActivity record rather than user
			/*
			this.reportReceived(
					StackUtil.stringFromElement(state, code, "Id"),
					StackUtil.stringFromElement(state, code, "AuditKey"),
					Struct.objectToRecord(StackUtil.refFromElement(state, code, "ReportData")),
					Struct.objectToRecord(StackUtil.refFromElement(state, code, "HandlerData"))
			);

			 */

			System.out.println("NOOP at this time.");

			return ReturnOption.CONTINUE;
		}

		return super.operation(state, code);
	}
}
