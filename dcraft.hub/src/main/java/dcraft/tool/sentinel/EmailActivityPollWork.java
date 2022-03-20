package dcraft.tool.sentinel;

import dcraft.db.DatabaseException;
import dcraft.db.proc.filter.CurrentRecord;
import dcraft.db.proc.filter.Unique;
import dcraft.db.request.query.SelectFields;
import dcraft.db.tables.TableUtil;
import dcraft.db.tables.TablesAdapter;
import dcraft.hub.app.ApplicationHub;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
import dcraft.hub.op.OperationOutcome;
import dcraft.hub.op.OperationOutcomeStruct;
import dcraft.interchange.aws.AWSSQS;
import dcraft.log.Logger;
import dcraft.service.ServiceHub;
import dcraft.service.ServiceRequest;
import dcraft.sql.SqlConnection;
import dcraft.sql.SqlUtil;
import dcraft.sql.SqlWriter;
import dcraft.struct.BaseStruct;
import dcraft.struct.ListStruct;
import dcraft.struct.RecordStruct;
import dcraft.struct.Struct;
import dcraft.task.IWork;
import dcraft.task.StateWork;
import dcraft.task.StateWorkStep;
import dcraft.task.TaskContext;
import dcraft.util.StringUtil;
import dcraft.util.TimeUtil;
import dcraft.xml.XElement;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.sql.Time;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

public class EmailActivityPollWork extends StateWork {
	static public EmailActivityPollWork of() {
		EmailActivityPollWork work = new EmailActivityPollWork();
		return work;
	}

	protected Deque<RecordStruct> sqsqueues = new ArrayDeque<>();
	protected RecordStruct sqsqueue = null;

	protected Deque<String> sqsmessageids = new ArrayDeque<>();

	protected StateWorkStep doInit = null;
	protected StateWorkStep collectMessages = null;
	protected StateWorkStep delteeMessage = null;
	protected StateWorkStep finish = null;

	@Override
	public void prepSteps(TaskContext trun) throws OperatingContextException {
		this
				.withStep(doInit = StateWorkStep.of("Init", this::doInit))
				.withStep(collectMessages = StateWorkStep.of("Collect Messages", this::doCollectMessages))
				.withStep(delteeMessage = StateWorkStep.of("Delete Message", this::doDeleteMessage))
				.withStep(finish = StateWorkStep.of("Finish", this::doFinish));
	}

	public StateWorkStep doInit(TaskContext trun) throws OperatingContextException {
		OperationContext.getOrThrow().touch();

		Logger.info("Start Checking SQS Queues for email notifications");

		try (SqlConnection conn = SqlUtil.getConnection("sentinel-readwrite")) {
			ListStruct sqsqueues = conn.getResults("SELECT sqs.Region, sqs.QueuePath, sqs.Id, acc.Alias " +
					"FROM dca_aws_sqs sqs INNER JOIN dca_aws_account acc ON (sqs.AccountId = acc.Id) " +
					"WHERE sqs.QueueType = ?", 1);

			for (int i = 0; i < sqsqueues.size(); i++) {
				RecordStruct account = sqsqueues.getItemAsRecord(i);

				this.sqsqueues.addLast(account);
			}

			return this.collectMessages;
		}
		catch (Exception x) {
			Logger.error("Error collecting message queues: " + x);
			return this.finish;
		}
	}

	public StateWorkStep doCollectMessages(TaskContext trun) throws OperatingContextException {
		OperationContext.getOrThrow().touch();

		this.sqsqueue = this.sqsqueues.pollFirst();

		if (this.sqsqueue == null)
			return this.finish;

		Long queueid = this.sqsqueue.getFieldAsInteger("Id");		// queue id
		String alias = this.sqsqueue.getFieldAsString("Alias");			// aws account alias
		String region = this.sqsqueue.getFieldAsString("Region");			// queue region
		String path = this.sqsqueue.getFieldAsString("QueuePath");			// queue path

		XElement settings = ApplicationHub.getCatalogSettings("Interchange-Aws-" + alias);

		if (settings == null) {
			Logger.warn("Missing settings Interchange-Aws for: " + alias + " - " + path);
			return StateWorkStep.REPEAT;
		}

		Logger.info("Polling queue: " + alias + " - " + path);

		// after we collect then go clear out those messages from the queue
		this.chainThen(trun, new IWork() {
			@Override
			public void run(TaskContext taskctxsch) throws OperatingContextException {
				AWSSQS.getMessages(settings, region, path, 10, new OperationOutcome<XElement>() {
					@Override
					public void callback(XElement result) throws OperatingContextException {
						System.out.println("Response:");
						System.out.println(result.toPrettyString());

						ListStruct cleanresults = AWSSQS.extractOutgoingEmailNotices(result);

						try (SqlConnection conn = SqlUtil.getConnection("sentinel-readwrite")) {
							for (int i = 0; i < cleanresults.size(); i++) {
								RecordStruct cleanresult = cleanresults.getItemAsRecord(i);

								String queuehandle = cleanresult.getFieldAsString("ReceiptHandle");
								String queuemsgid = cleanresult.getFieldAsString("QueueMessageId");
								RecordStruct message = cleanresult.getFieldAsRecord("Payload");

								String ntype = message.getFieldAsString("notificationType");

								// see docs https://docs.aws.amazon.com/ses/latest/DeveloperGuide/notification-contents.html
								// Permanent bounces should be automatically suppressed

								int rtype = 1;  // Delivery

								if (ntype.equals("Bounce")) {
									rtype = 2;

									if ("Transient".equals(message.selectAsString("bounce.bounceType"))
										&& "General".equals(message.selectAsString("bounce.bounceSubType"))
										&& ! "failed".equals(message.selectAsString("bouncedRecipients.0.action"))) {
										rtype = 1;		// if not failed then chances are this was a success - it was likely just an auto response
									}
								}
								else if (ntype.equals("Complaint")) {
									rtype = 3;

									if ("not-spam".equals(message.selectAsString("complaint.complaintFeedbackType"))) {
										rtype = 1;		// reported as not spam - so treat as delivered
									}
								}

								// always insert even though occasionally we get a duplicate from SQS - that is okay because field QueueHandle is unique
								// and therefore the new insert will be ignored and all is well

								SqlWriter insertEmailActivity = SqlWriter.insert("dca_email_activity")
									.with("QueueId", queueid)
									.with("QueueHandle", queuehandle)
									.with("QueueMsgId", queuemsgid)
									.with("RecordedAt", cleanresult.getFieldAsDateTime("Timestamp"))
									.with("ReportStatus", 0)
									.with("ReportType", rtype)
									.with("MessageId", "<" + message.selectAsString("mail.messageId") + "@email.amazonses.com>")
									.with("Message", message);

								conn.executeWrite(insertEmailActivity);

								sqsmessageids.addLast(queuehandle);		// don't do this until after SQL, so if SQL fails we don't delete from SQS
							}
						}
						catch (Exception x) {
							Logger.warn("Error adding email activity records: " + x);
						}

						// get for clean up

						cleanresults = AWSSQS.extractIncomingEmailNotices(result);

						for (int i = 0; i < cleanresults.size(); i++) {
							RecordStruct cleanresult = cleanresults.getItemAsRecord(i);

							String queuehandle = cleanresult.getFieldAsString("ReceiptHandle");

							Logger.warn("Got incoming email notice on an email feedback queue. Scheduled for removal: " + cleanresult);

							sqsmessageids.addLast(queuehandle);		// don't do this until after SQL, so if SQL fails we don't delete from SQS
						}

						taskctxsch.returnEmpty();
					}
				});
			}
		}, this.delteeMessage);

		return StateWorkStep.WAIT;
	}

	public StateWorkStep doDeleteMessage(TaskContext trun) throws OperatingContextException {
		OperationContext.getOrThrow().touch();

		String mhandle = this.sqsmessageids.pollFirst();

		if (StringUtil.isEmpty(mhandle))
			return this.collectMessages;

		Logger.info("Deleting from queue: " + mhandle);

		String alias = this.sqsqueue.getFieldAsString("Alias");			// aws account alias
		String region = this.sqsqueue.getFieldAsString("Region");			// queue region
		String path = this.sqsqueue.getFieldAsString("QueuePath");			// queue path

		XElement settings = ApplicationHub.getCatalogSettings("Interchange-Aws-" + alias);

		this.chainThenRepeat(trun, new IWork() {
			@Override
			public void run(TaskContext taskctxsch) throws OperatingContextException {
				AWSSQS.deleteMessage(settings, region, path, mhandle, new OperationOutcome<XElement>() {
					@Override
					public void callback(XElement result) throws OperatingContextException {
						if (this.hasErrors())
							Logger.warn("Problem deleting from queue: " + mhandle);
						else
							Logger.info("Deleted from queue: " + mhandle);

						taskctxsch.returnEmpty();
					}
				});
			}
		});

		return StateWorkStep.WAIT;
	}

	public StateWorkStep doFinish(TaskContext trun) throws OperatingContextException {
		Logger.info("Finish Checking SQS Queues for email notifications");

		return StateWorkStep.STOP;		// needed so we don't flag "complete" on previous step
	}
}
