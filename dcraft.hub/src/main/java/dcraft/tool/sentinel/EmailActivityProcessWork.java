package dcraft.tool.sentinel;

import dcraft.hub.app.ApplicationHub;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
import dcraft.hub.op.OperationOutcomeEmpty;
import dcraft.log.Logger;
import dcraft.service.portable.PortableMessageUtil;
import dcraft.sql.SqlConnection;
import dcraft.sql.SqlUtil;
import dcraft.sql.SqlWriter;
import dcraft.struct.ListStruct;
import dcraft.struct.RecordStruct;
import dcraft.task.IWork;
import dcraft.task.StateWork;
import dcraft.task.StateWorkStep;
import dcraft.task.TaskContext;
import dcraft.tool.backup.BackupUtil;

import java.util.ArrayDeque;
import java.util.Deque;

public class EmailActivityProcessWork extends StateWork {
	static public EmailActivityProcessWork of() {
		return new EmailActivityProcessWork();
	}

	protected Deque<RecordStruct> reports = new ArrayDeque<>();

	protected RecordStruct currreport = null;
	protected Long currtenant = null;

	protected StateWorkStep init = null;
	protected StateWorkStep collectReports = null;
	protected StateWorkStep processReport = null;
	protected StateWorkStep queueReport = null;
	protected StateWorkStep finish = null;

	@Override
	public void prepSteps(TaskContext trun) throws OperatingContextException {
		this
				.withStep(this.init = StateWorkStep.of("Init", this::doInit))
				.withStep(this.collectReports = StateWorkStep.of("Collect Reports", this::doCollectReports))
				.withStep(this.processReport = StateWorkStep.of("Process Reports", this::doProcessReport))
				.withStep(this.queueReport = StateWorkStep.of("Queue Reports", this::doQueueWork))
				.withStep(this.finish = StateWorkStep.of("Finish", this::doFinish));
	}

	public StateWorkStep doInit(TaskContext trun) throws OperatingContextException {
		OperationContext.getOrThrow().touch();

		Logger.info("Start processing for email notifications");

		return this.collectReports;
	}

	public StateWorkStep doCollectReports(TaskContext trun) throws OperatingContextException {
		OperationContext.getOrThrow().touch();

		try (SqlConnection conn = SqlUtil.getConnection("sentinel-readwrite")) {
			ListStruct reports = conn.getResults("SELECT Id, QueueId, MessageId, Message, RecordedAt, ReportType FROM dca_email_activity " +
					"WHERE ReportStatus = 0 ORDER BY RecordedAt ASC LIMIT 50");

			for (int i = 0; i < reports.size(); i++) {
				RecordStruct report = reports.getItemAsRecord(i);

				this.reports.addLast(report);
			}
		}
		catch (Exception x) {
			Logger.error("Error collecting email reports: " + x);
			return this.finish;
		}

		return this.processReport;
	}

	/*
		Learn more about notification types:

		https://docs.aws.amazon.com/ses/latest/DeveloperGuide/notification-contents.html
	 */
	public StateWorkStep doProcessReport(TaskContext trun) throws OperatingContextException {
		OperationContext.getOrThrow().touch();

		RecordStruct report = this.reports.pollFirst();

		if (report == null)
			return this.finish;

		this.currreport = null;
		this.currtenant = null;

		Logger.info("Processing report: " + report.getFieldAsString("MessageId"));

		Long reportid = report.getFieldAsInteger("Id");
		Long reportType = report.getFieldAsInteger("ReportType");
		RecordStruct message = report.getFieldAsRecord("Message");

		try (SqlConnection conn = SqlUtil.getConnection("sentinel-readwrite")) {
			if (message == null) {
				SqlWriter updateEmailActivity = SqlWriter.update("dca_email_activity", reportid)
						.with("ReportStatus", 3)
						.with("Note", "Unable to parse report messages");

				conn.executeWrite(updateEmailActivity);

				BackupUtil.notifyProgress(ApplicationHub.getDeployment() + " : " + ApplicationHub.getNodeId() + " : error parsing email report: " + reportid);
			}
			else {
				RecordStruct mailRec = message.getFieldAsRecord("mail");

				String domain = mailRec.getFieldAsString("source");
				this.currtenant = null;

				domain = domain.substring(domain.indexOf('@') + 1);

				System.out.println("Checking source domain: " + domain);

				Long countwebmasters = conn.getVarInteger("SELECT COUNT(Id) FROM dca_agency WHERE WebmasterDomains LIKE ? ESCAPE '!'",
						"%" + SqlUtil.escLike(domain) + "%");

				if (countwebmasters > 0) {
					System.out.println("Found webmaster domain: " + domain + " - checking destinations");

					ListStruct destinations = mailRec.getFieldAsList("destination");

					for (int i = 0; i < destinations.size(); i++) {
						String destemail = destinations.getItemAsString(i);

						String destdomain = destemail.substring(destemail.indexOf('@') + 1);

						System.out.println("Matching dest domain: " + destdomain);

						this.currtenant = this.matchDomainToTenant(conn, destdomain);

						if (this.currtenant == null) {
							System.out.println("Matching dest address: " + destemail);

							this.currtenant = this.matchEmailToTenant(conn, destemail);
						}
					}
				}
				else {
					System.out.println("Matching source domain: " + domain);

					this.currtenant = this.matchDomainToTenant(conn, domain);
				}

				boolean tenantavail = false;
				RecordStruct deliveryInfo = null;

				if (this.currtenant != null) {
					deliveryInfo = conn.getRow("SELECT ten.Alias TenantAlias, dep.OnlineStatus, dep.Alias DeploymentAlias, dep.ReportingDomain FROM dca_deployment_tenant ten INNER JOIN dca_deployment dep ON (ten.DeploymentId = dep.Id) WHERE ten.Id = ?",
							this.currtenant);

					if ((deliveryInfo != null) && deliveryInfo.getFieldAsInteger("OnlineStatus") > 0L)
						tenantavail = true;
				}

				// TODO if tenant OnlineStatus is 0 then report similar to no tenant, but make the association in the table

				if (! tenantavail) {
					SqlWriter updateEmailActivity = SqlWriter.update("dca_email_activity", reportid)
							.with("ReportStatus", 3)
							.with("Note", "Unable to report to tenant");

					if (this.currtenant != null)
						updateEmailActivity.with("RelatedTenantId", this.currtenant);

					conn.executeWrite(updateEmailActivity);

					if (reportType == 1) {
						Logger.info("Got email DELIVERY report, unable to report to tenant, skipping. Id: " + reportid);
					}
					else if (reportType == 2) {
						Logger.warn("Got email BOUNCE report, unable to report to tenant, reporting to primary webmaster. Id: " + reportid);

						// TODO notify primary webmaster instead

						BackupUtil.notifyProgress(ApplicationHub.getDeployment() + " : " + ApplicationHub.getNodeId() + " : unable to route email bounce report: " + reportid);
					}
					else if (reportType == 3) {
						Logger.warn("Got email COMPLAINT report, unable to report to tenant, reporting to primary webmaster. Id: " + reportid);

						// TODO notify primary webmaster instead

						BackupUtil.notifyProgress(ApplicationHub.getDeployment() + " : " + ApplicationHub.getNodeId() + " : unable to route email complaint report: " + reportid);
					}
				}
				else {
					// TODO
					// TODO queue notice to the domain in question - have an expire time so we don't keep trying forever
					// TODO

					if (reportType == 1) {
						Logger.info("Got email DELIVERY report, tenant found: " + this.currtenant + " Report Id: " + reportid);

						// TODO notify tenant instead -- remove someday

						BackupUtil.notifyProgress(ApplicationHub.getDeployment() + " : " + ApplicationHub.getNodeId() + " : successfully routed email delivery report: " + reportid);
					}
					else if (reportType == 2) {
						Logger.warn("Got email BOUNCE report, tenant found: " + this.currtenant + " Report Id: " + reportid);

						// TODO notify tenant instead -- remove someday

						BackupUtil.notifyProgress(ApplicationHub.getDeployment() + " : " + ApplicationHub.getNodeId() + " : successfully routed email bounce report: " + reportid);
					}
					else if (reportType == 3) {
						Logger.warn("Got email COMPLAINT report, tenant found: " + this.currtenant + " Report Id: " + reportid);

						// TODO notify tenant instead -- remove someday

						BackupUtil.notifyProgress(ApplicationHub.getDeployment() + " : " + ApplicationHub.getNodeId() + " : successfully routed email complaint report: " + reportid);
					}

					SqlWriter updateEmailActivity = SqlWriter.update("dca_email_activity", reportid)
							.with("ReportStatus", 1)
							.with("RelatedTenantId", this.currtenant);

					conn.executeWrite(updateEmailActivity);

					// send a notice to this deployment

					this.currreport = RecordStruct.record()
							.with("ReportId", reportid)
							.with("ReportAt", report.getField("RecordedAt"))
							.with("ReportType", reportType)
							.with("Message", message);

					return this.queueReport;
				}
			}
		}
		catch (Exception x) {
			Logger.error("Error updating email report: " + reportid + " - " + x);
		}

		// if not queuing a report then repeat
		return StateWorkStep.REPEAT;
	}

	public StateWorkStep doQueueWork(TaskContext trun) throws OperatingContextException {
		OperationContext.getOrThrow().touch();

		if (this.currreport == null)
			return this.processReport;

		Logger.info("Queue report: " + this.currreport.getFieldAsString("ReportId"));

		this.chainThen(trun, new IWork() {
			@Override
			public void run(TaskContext taskctxsch) throws OperatingContextException {
				// settings, region, path, message.toSignedMessage(deployment, tenant), 10L,

				PortableMessageUtil.sendMessageToRemoteTenantByQueue(EmailActivityProcessWork.this.currtenant, "dcmServices.Reports.OutboundEmailActivity",
						EmailActivityProcessWork.this.currreport, 10L, null, new OperationOutcomeEmpty()
				{
					@Override
					public void callback() throws OperatingContextException {
						if (this.hasErrors()) {
							Logger.warn("Problem sending to inbox queue: " + EmailActivityProcessWork.this.currreport.getFieldAsString("ReportId"));
						}
						else {
							Logger.info("Sent to inbox queue: " + EmailActivityProcessWork.this.currreport.getFieldAsString("ReportId"));

							try (SqlConnection conn = SqlUtil.getConnection("sentinel-readwrite")) {
								SqlWriter updateEmailActivity = SqlWriter.update("dca_email_activity", EmailActivityProcessWork.this.currreport.getFieldAsString("ReportId"))
										.with("ReportStatus", 2);

								conn.executeWrite(updateEmailActivity);
							}
							catch (Exception x) {
								Logger.error("Unable to record that report message queued.");
							}
						}

						taskctxsch.returnEmpty();
					}
				});
			}
		}, this.processReport);

		return StateWorkStep.WAIT;
	}

	// TODO add a step to pick up anyone stuck in ReportStatus = 1 and then run them through the queue

	public StateWorkStep doFinish(TaskContext trun) throws OperatingContextException {
		Logger.info("Finish processing for email notifications");

		return StateWorkStep.STOP;		// needed so we don't flag "complete" on previous step
	}

	// utility

	public Long matchDomainToTenant(SqlConnection conn, String domain) throws OperatingContextException {
		return conn.getVarInteger("SELECT Id FROM dca_deployment_tenant WHERE EmailDomains LIKE ? ESCAPE '!';",
				"%" + SqlUtil.escLike(domain) + "%");
	}

	public Long matchEmailToTenant(SqlConnection conn, String email) throws OperatingContextException {
		return conn.getVarInteger("SELECT Id FROM dca_deployment_tenant WHERE EmailDestinations LIKE ? ESCAPE '!';",
				"%" + SqlUtil.escLike(email) + "%");
	}
}
