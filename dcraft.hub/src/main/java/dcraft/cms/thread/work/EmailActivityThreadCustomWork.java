package dcraft.cms.thread.work;

import dcraft.cms.reports.db.EmailActivityUtil;
import dcraft.db.BasicRequestContext;
import dcraft.db.IRequestContext;
import dcraft.db.tables.TablesAdapter;
import dcraft.hub.app.ApplicationHub;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
import dcraft.log.Logger;
import dcraft.sql.SqlConnection;
import dcraft.sql.SqlUtil;
import dcraft.sql.SqlWriter;
import dcraft.struct.ListStruct;
import dcraft.struct.RecordStruct;
import dcraft.struct.Struct;
import dcraft.task.StateWork;
import dcraft.task.StateWorkStep;
import dcraft.task.TaskContext;
import dcraft.tool.backup.BackupUtil;
import dcraft.util.MailUtil;
import dcraft.util.StringUtil;

import java.sql.SQLException;
import java.time.ZonedDateTime;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

public class EmailActivityThreadCustomWork extends StateWork {
	static public EmailActivityThreadCustomWork of(String actid, String auditkey) {
		EmailActivityThreadCustomWork work = new EmailActivityThreadCustomWork();
		work.actid = actid;
		work.auditkey = auditkey;
		return work;
	}

	protected String actid = null;
	protected String auditkey = null;
	protected String reportid = null;

	protected Deque<RecordStruct> reports = new ArrayDeque<>();

	protected StateWorkStep init = null;
	protected StateWorkStep recordStatus = null;
	protected StateWorkStep messageTypeTrigger = null;
	protected StateWorkStep notifySentinel = null;
	protected StateWorkStep finish = null;

	@Override
	public void prepSteps(TaskContext trun) throws OperatingContextException {
		this
				.withStep(init = StateWorkStep.of("Init", this::doInit))
				.withStep(recordStatus = StateWorkStep.of("Record Receipient Status", this::doRecordReceipientStatus))
				.withStep(messageTypeTrigger = StateWorkStep.of("Call Message Type Trigger", this::doMessageTypeTrigger))
				.withStep(notifySentinel = StateWorkStep.of("Notify Sentinel", this::doNotifySentinel))
				.withStep(finish = StateWorkStep.of("Finish", this::doFinish));
	}

	public StateWorkStep doInit(TaskContext trun) throws OperatingContextException {
		OperationContext.getOrThrow().touch();

		Logger.info("Start processing for Thread custom email activity notifications");

		return this.recordStatus;
	}

	public StateWorkStep doRecordReceipientStatus(TaskContext trun) throws OperatingContextException {
		OperationContext.getOrThrow().touch();

		TablesAdapter db = TablesAdapter.of(BasicRequestContext.ofDefaultDatabase());

		this.reportid = Struct.objectToString(db.getList("dcmOutboundEmailActivity", actid, "dcmReportId", auditkey));
		Long reportType = Struct.objectToInteger(db.getList("dcmOutboundEmailActivity", actid, "dcmReportType", auditkey));
		ZonedDateTime reportAt = Struct.objectToDateTime(db.getList("dcmOutboundEmailActivity", actid, "dcmReportAt", auditkey));
		RecordStruct reportMessage = Struct.objectToRecord(db.getList("dcmOutboundEmailActivity", actid, "dcmReportMessage", auditkey));

		RecordStruct handlerData = Struct.objectToRecord(db.getScalar("dcmOutboundEmailActivity", actid, "dcmHandlerData"));

		/*
			Learn more about notification types:
			https://docs.aws.amazon.com/ses/latest/DeveloperGuide/notification-contents.html
		 */

		String notiType = reportMessage.getFieldAsString("notificationType");

		ListStruct recipients = ListStruct.list();

		if ("Delivery".equals(notiType)) {
			ListStruct dlist = reportMessage.selectAsList("delivery.recipients");

			for (int i = 0; i < dlist.size(); i++) {
				String address = MailUtil.cleanEmailDomainName(dlist.getItemAsString(i));

				if (StringUtil.isNotEmpty(address))
					recipients.with(address);
			}
		}
		else if ("Bounce".equals(notiType)) {
			ListStruct bouncelist = reportMessage.selectAsList("bounce.bouncedRecipients");

			for (int i = 0; i < bouncelist.size(); i++) {
				RecordStruct bounceuser = bouncelist.getItemAsRecord(i);

				String address = MailUtil.cleanEmailDomainName(bounceuser.getFieldAsString("emailAddress"));

				if (StringUtil.isNotEmpty(address))
					recipients.with(address);
			}
		}
		else if ("Complaint".equals(notiType)) {
			ListStruct complist = reportMessage.selectAsList("complaint.complainedRecipients");

			for (int i = 0; i < complist.size(); i++) {
				RecordStruct compuser = complist.getItemAsRecord(i);

				String address = MailUtil.cleanEmailDomainName(compuser.getFieldAsString("emailAddress"));

				if (StringUtil.isNotEmpty(address))
					recipients.with(address);
			}
		}

		String state = "Delivery";

		if (reportType == 1L) {
			// this is a Not Spam "complaint"
			if ("Complaint".equals(notiType)) {
				for (int i = 0; i < recipients.size(); i++) {
					EmailActivityUtil.unsuppressEmail(db, recipients.getItemAsString(i), actid);
				}
			}
		}
		else if (reportType == 2L) {
			state = "Bounce";

			// report type 2 could happen even if not a bounce object - just go with this
			if ("Permanent".equals(reportMessage.selectAsString("bounce.bounceType"))) {
				// add ALL to suppression list
				for (int i = 0; i < recipients.size(); i++) {
					EmailActivityUtil.suppressEmail(db, recipients.getItemAsString(i), actid);
				}
			}
		}
		else if (reportType == 3L) {
			state = "Complaint";

			/*
				"Most ISPs remove the email address of the recipient who submitted the complaint from their complaint notification.
				For this reason, this list contains information about recipients who might have sent the complaint, based on the
				recipients of the original message and the ISP from which we received the complaint."

				So only add if we can be sure this was the complainer
			 */
			if (recipients.size() == 1) {
				// add to suppression list
				EmailActivityUtil.suppressEmail(db, recipients.getItemAsString(0), actid);
			}
		}

		String tid = handlerData.getFieldAsString("ThreadId");

		List<String> keys = db.getListKeys("dcmThread", tid, "dcmEmailAddress");

		for (int i = 0; i < recipients.size(); i++) {
			String address = recipients.getItemAsString(i);

			if (keys.contains(address))
				db.updateList("dcmThread", tid, "dcmEmailState", address, state);
			else
				BackupUtil.notifyProgress(ApplicationHub.getDeployment() + " : " + ApplicationHub.getNodeId() + " : address mismatch in reported email " + address + " report: " + reportid);
		}

		// TODO if complaint then indicate to web master

		BackupUtil.notifyProgress(ApplicationHub.getDeployment() + " : " + ApplicationHub.getNodeId() + " : successfully reported email " + state + " report: " + reportid);

		return this.messageTypeTrigger;
	}

	public StateWorkStep doMessageTypeTrigger(TaskContext trun) throws OperatingContextException {
		OperationContext.getOrThrow().touch();

		// TODO add support for message type trigger event - for a notice or such

		return this.notifySentinel;
	}

	public StateWorkStep doNotifySentinel(TaskContext trun) throws OperatingContextException {
		OperationContext.getOrThrow().touch();

		// TODO new reportid

		return this.finish;
	}

	public StateWorkStep doFinish(TaskContext trun) throws OperatingContextException {
		Logger.info("Finish processing for Thread custom email activity notifications");

		return StateWorkStep.STOP;		// needed so we don't flag "complete" on previous step
	}

}
