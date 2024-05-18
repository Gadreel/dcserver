package dcraft.mail;

import dcraft.db.ICallContext;
import dcraft.db.proc.IStoredProc;
import dcraft.db.request.update.DbRecordRequest;
import dcraft.db.request.update.InsertRecordRequest;
import dcraft.db.tables.TableUtil;
import dcraft.db.tables.TablesAdapter;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationOutcomeStruct;
import dcraft.log.Logger;
import dcraft.service.portable.PortableMessageUtil;
import dcraft.struct.ListStruct;
import dcraft.struct.RecordStruct;
import dcraft.struct.Struct;
import dcraft.util.StringUtil;
import dcraft.util.TimeUtil;

import java.time.ZonedDateTime;

public class OutboundEmailActivity implements IStoredProc {
	@Override
	public void execute(ICallContext request, OperationOutcomeStruct callback) throws OperatingContextException {
		TablesAdapter db = TablesAdapter.of(request);

		RecordStruct data = request.getDataAsRecord();

		RecordStruct reportMessage = data.getFieldAsRecord("Message");

		String sendid = null;
		String auditkey = null;

		// try aws reporting system

		String aws = reportMessage.selectAsString("mail.sourceArn");

		/*
		  "mail": {
			"sourceArn": "arn:aws:ses:us-east-1:213513686763:identity/orangetreeimports.com",
			"sendingAccountId": "213513686763",
			"sourceIp": "52.14.62.255",
			"callerIdentity": "ses-smtp-user.20150819-165602",
			"destination": [
			  "aahong1@sbcglobal.net"
			],
			"messageId": "0100018c79a57e12-8896530c-1376-4e47-9c84-897942848e72-000000",
			"source": "no-reply@orangetreeimports.com",
			"timestamp": "2023-12-17T21:18:58.066Z"
		  },
		 */

		String reportid = data.getFieldAsString("ReportId");
		String msgid = null;

		if (StringUtil.isNotEmpty(aws)) {
			msgid = reportMessage.selectAsString("mail.messageId");

			if (StringUtil.isEmpty(msgid)) {
				Logger.warn("Unable to process OutboundEmailActivity, missing messageId.");
			}
			else {
				msgid = "<" + msgid + "@email.amazonses.com>";

				Long reportType = data.getFieldAsInteger("ReportType");
				ZonedDateTime reportAt = data.getFieldAsDateTime("ReportAt");

				auditkey = TimeUtil.stampFmt.format(reportAt);

				sendid = Struct.objectToString(db.firstInIndex("dccCommSend", "dccSendIndex", "email:" + msgid, false));

				if (StringUtil.isNotEmpty(sendid)) {
					Logger.info("Tracking email feedback report: " + reportid + " - message id " + msgid + " matches our dccCommSend records.");

					String type = (reportType == 2) ? "Bounce" : (reportType == 3) ? "Complaint" : "Delivery";

					db.updateList("dccCommSend", sendid, "dccActivityStamp", auditkey, reportAt);
					db.updateList("dccCommSend", sendid, "dccActivityType", auditkey, type);
					db.updateList("dccCommSend", sendid, "dccActivityData", auditkey, reportMessage);
					db.updateList("dccCommSend", sendid, "dccActivityReportId", auditkey, reportid);
					db.updateList("dccCommSend", sendid, "dccActivityReportIndex", auditkey, "email:" + reportid);

					ListStruct addresses = null;

					if (reportType == 1) {
						addresses = reportMessage.selectAsList("delivery.recipients");

						// in the case of a "not spam" report
						if (addresses == null) {
							addresses = ListStruct.list();

							ListStruct caddresses = reportMessage.selectAsList("complaint.complainedRecipients");

							for (int i = 0; i < caddresses.size(); i++)
								addresses.with(caddresses.getItemAsRecord(i).getFieldAsString("emailAddress"));
						}
					}
					else if (reportType == 2) {
						addresses = ListStruct.list();

						ListStruct baddresses = reportMessage.selectAsList("bounce.bouncedRecipients");

						for (int i = 0; i < baddresses.size(); i++)
							addresses.with(baddresses.getItemAsRecord(i).getFieldAsString("emailAddress"));
					}
					else if (reportType == 3) {
						addresses = ListStruct.list();

						ListStruct caddresses = reportMessage.selectAsList("complaint.complainedRecipients");

						for (int i = 0; i < caddresses.size(); i++)
							addresses.with(caddresses.getItemAsRecord(i).getFieldAsString("emailAddress"));
					}

					if (addresses != null) {
						ListStruct targets = ListStruct.list();

						for (int i = 0; i < addresses.size(); i++) {
							String trackid = CommUtil.ensureCommTrack("email", addresses.getItemAsString(i), null);

							if (StringUtil.isNotEmpty(trackid))
								targets.with(trackid);
						}

						db.updateList("dccCommSend", sendid, "dccActivityReportTargets", auditkey, targets);
					}
				}
				else {
					Logger.info("Auditing email feedback report: " + reportid + " - message id " + msgid + " does not match our dccCommSend records.");

					String actid = Struct.objectToString(db.firstInIndex("dcmOutboundEmailActivity", "dcmMessageId", msgid, false));

					if (StringUtil.isEmpty(actid)) {
						DbRecordRequest req = InsertRecordRequest.insert()
								.withTable("dcmOutboundEmailActivity")
								.withUpdateField("dcmMessageId", msgid)
								.withUpdateField("dcmCreatedAt", TimeUtil.now());

						actid = TableUtil.updateRecord(db, req);
					}

					db.updateList("dcmOutboundEmailActivity", actid, "dcmReportAt", auditkey, reportAt);
					db.updateList("dcmOutboundEmailActivity", actid, "dcmReportId", auditkey, data.getFieldAsString("ReportId"));
					db.updateList("dcmOutboundEmailActivity", actid, "dcmReportType", auditkey, reportType);
					db.updateList("dcmOutboundEmailActivity", actid, "dcmReportMessage", auditkey, reportMessage);
				}
			}
		}
		else {
			Logger.warn("Unable to process OutboundEmailActivity, unknown reporting service.");
		}

		// no matter the reporting system -

		if (StringUtil.isNotEmpty(sendid))
			CommUtil.reviewSend(sendid, auditkey, reportid);

		RecordStruct finalsendinfo = PortableMessageUtil.sendToForSentinel(null);

		if (finalsendinfo != null) {
			// report to sentinel that we processed this message
			RecordStruct finalreport = PortableMessageUtil.buildCoreMessage(null, null);

			finalreport
					.with("Destination", RecordStruct.record()
							.with("Deployment", "sentinel")            // TODO configure these two
							.with("Tenant", "root")
					)
					.with("Payload", RecordStruct.record()
							.with("Op", "dcmServices.Reports.OutboundEmailActivityConfirm")
							.withConditional("Body", RecordStruct.record()
									.with("ReportId", data.getFieldAsString("ReportId"))
									.with("ActivityId", sendid)
									.with("Note", "Basic activity record")
							)
					);

			PortableMessageUtil.sendMessageByQueue(finalreport, finalsendinfo, null, null);
		}

		if (StringUtil.isNotEmpty(msgid)) {
			callback.returnValue(RecordStruct.record()
					.with("MessageId", msgid)
			);
		}
		else {
			callback.returnEmpty();
		}
	}
}
