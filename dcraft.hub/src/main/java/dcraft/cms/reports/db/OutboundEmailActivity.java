package dcraft.cms.reports.db;

import dcraft.db.Constants;
import dcraft.db.ICallContext;
import dcraft.db.proc.IStoredProc;
import dcraft.db.request.update.DbRecordRequest;
import dcraft.db.request.update.InsertRecordRequest;
import dcraft.db.tables.TableUtil;
import dcraft.db.tables.TablesAdapter;
import dcraft.hub.app.ApplicationHub;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationOutcomeStruct;
import dcraft.log.Logger;
import dcraft.service.ServiceHub;
import dcraft.service.ServiceRequest;
import dcraft.service.portable.PortableMessageUtil;
import dcraft.struct.BaseStruct;
import dcraft.struct.ListStruct;
import dcraft.struct.RecordStruct;
import dcraft.struct.Struct;
import dcraft.util.StringUtil;
import dcraft.util.TimeUtil;
import dcraft.xml.XElement;

import java.time.ZonedDateTime;

public class OutboundEmailActivity implements IStoredProc {
	@Override
	public void execute(ICallContext request, OperationOutcomeStruct callback) throws OperatingContextException {
		TablesAdapter db = TablesAdapter.of(request);

		RecordStruct data = request.getDataAsRecord();

		RecordStruct reportMessage = data.getFieldAsRecord("Message");

		String msgid = reportMessage.selectAsString("mail.messageId");

		if (StringUtil.isEmpty(msgid)) {
			Logger.error("Unable to process OutboundEmailActivity, missing messageId.");
			callback.returnEmpty();
			return;
		}

		msgid = "<" + msgid + "@email.amazonses.com>";		// TODO review this assumption

		String actid = EmailActivityUtil.createGetRecord(db, msgid);

		Long reportType = data.getFieldAsInteger("ReportType");
		ZonedDateTime reportAt = data.getFieldAsDateTime("ReportAt");

		String auditkey = TimeUtil.stampFmt.format(reportAt);

		db.updateList("dcmOutboundEmailActivity", actid, "dcmReportAt", auditkey, reportAt);
		db.updateList("dcmOutboundEmailActivity", actid, "dcmReportId", auditkey, data.getFieldAsString("ReportId"));
		db.updateList("dcmOutboundEmailActivity", actid, "dcmReportType", auditkey, reportType);
		db.updateList("dcmOutboundEmailActivity", actid, "dcmReportMessage", auditkey, reportMessage);

		if (EmailActivityUtil.triggerReportHandler(db, actid, auditkey, data)) {
			RecordStruct finalsendinfo = PortableMessageUtil.sendToForSentinel(null);

			if (finalsendinfo != null) {
				// report to sentinel that we processed this message
				RecordStruct finalreport = PortableMessageUtil.buildCoreMessage(null, null);

				finalreport
						.with("Destination", RecordStruct.record()
								.with("Deployment", "sentinel")			// TODO configure these two
								.with("Tenant", "root")
						)
						.with("Payload", RecordStruct.record()
								.with("Op", "dcmServices.Reports.OutboundEmailActivityConfirm")
								.withConditional("Body", RecordStruct.record()
										.with("ReportId", data.getFieldAsString("ReportId"))
										.with("ActivityId", actid)
										.with("Note", "Basic activity record")
								)
						);

				PortableMessageUtil.sendMessageByQueue(finalreport, finalsendinfo, null, null);
			}
		}

		callback.returnValue(RecordStruct.record()
				.with("Id", actid)
		);
	}
}
