package dcraft.service.work;

import dcraft.hub.app.ApplicationHub;
import dcraft.hub.op.*;
import dcraft.interchange.aws.AWSSQS;
import dcraft.log.Logger;
import dcraft.service.portable.PortableMessageUtil;
import dcraft.struct.*;
import dcraft.task.*;
import dcraft.util.StringUtil;
import dcraft.xml.XElement;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

public class InBoxQueuePollWork extends StateWork {
	static public InBoxQueuePollWork of(String alt) {
		InBoxQueuePollWork work = new InBoxQueuePollWork();
		work.awsalt = alt;
		work.withoutFailOnErrors();		// don't fail, go to next step
		return work;
	}

	protected XElement awssettings = null;
	protected String awsalt = null;
	protected AtomicLong callcount = new AtomicLong();
	protected AtomicBoolean stopPolling = new AtomicBoolean(false);
	protected AtomicReference<RecordStruct> message = new AtomicReference<>();

	protected StateWorkStep doInit = null;
	protected StateWorkStep collectMessage = null;
	protected StateWorkStep extractMessage = null;
	protected StateWorkStep handleMessage = null;
	protected StateWorkStep deleteMessage = null;
	protected StateWorkStep finish = null;

	@Override
	public void prepSteps(TaskContext trun) throws OperatingContextException {
		this
				.withStep(this.doInit = StateWorkStep.of("Init", this::doInit))
				.withStep(this.collectMessage = StateWorkStep.of("Collect Queue Message", this::doCollectMessages))
				.withStep(this.extractMessage = StateWorkStep.of("Extract Payload Message", this::doExtractPayload))
				.withStep(this.handleMessage = StateWorkStep.of("Handle Payload Message", this::doHandlePayload))
				.withStep(this.deleteMessage = StateWorkStep.of("Delete Queue Message", this::doDeleteMessage))
				.withStep(this.finish = StateWorkStep.of("Finish", this::doFinish));
	}

	public StateWorkStep doInit(TaskContext trun) throws OperatingContextException {
		OperationContext.getOrThrow().touch();

		Logger.info("Start Checking SQS Queues for service messages");

		this.awssettings = ApplicationHub.getCatalogSettings("Interchange-Aws", this.awsalt);

		if ((this.awssettings == null) || ! this.awssettings.hasAttribute("SQSRegion")) {
			Logger.warn("Missing settings for Interchange-Aws SQS");
			return this.finish;
		}

		return this.collectMessage;
	}

	public StateWorkStep doCollectMessages(TaskContext trun) throws OperatingContextException {
		OperationContext.getOrThrow().touch();

		if (this.stopPolling.get())
			return this.finish;

		// grab no more than 100 per run
		if (this.callcount.get() >= 100L)
			return this.finish;

		String region = this.awssettings.getAttribute("SQSRegion");			// queue region
		String path = this.awssettings.getAttribute("InBoxQueue");			// queue path

		Logger.info("Polling my InBox queue: " + path);

		this.message.set(null);
		this.callcount.incrementAndGet();

		// after we collect then go clear out those messages from the queue
		this.chainThen(trun, new IWork() {
			@Override
			public void run(TaskContext taskctxsch) throws OperatingContextException {
				// get only one message - we need time to process it and shouldn't hold up any other workers trying to poll
				AWSSQS.getMessages(InBoxQueuePollWork.this.awssettings, region, path, 1, new OperationOutcome<>() {
					@Override
					public void callback(XElement result) throws OperatingContextException {
						if (this.hasErrors()) {
							InBoxQueuePollWork.this.stopPolling.set(true);
						}
						else {
							System.out.println("Response:");
							System.out.println(result.toPrettyString());

							try (OperationMarker om = OperationMarker.create()) {
								// TODO for some reason if this fails we aren't deleting the message - we should, so look into this

								ListStruct cleanresults = AWSSQS.extractIncomingMessages(result);

								// do only one - should only be one
								if (cleanresults.size() > 0)
									InBoxQueuePollWork.this.message.set(cleanresults.selectAsRecord("0"));
								// if 0 returned and no errors then
								else if (! om.hasErrors())
									InBoxQueuePollWork.this.stopPolling.set(true);

								// if errors then continue - don't let a bad sig scare us (or stop our queue progress)
								//if (om.hasErrors())
								//	trun.clearExitCode();
							}
						}

						taskctxsch.returnEmpty();
					}
				});
			}
		}, this.extractMessage);

		return StateWorkStep.WAIT;
	}

	public StateWorkStep doExtractPayload(TaskContext trun) throws OperatingContextException {
		OperationContext.getOrThrow().touch();

		if (this.message.get() == null)
			return this.collectMessage;

		String mhandle = this.message.get().getFieldAsString("ReceiptHandle");

		if (StringUtil.isEmpty(mhandle))
			return this.collectMessage;

		Logger.info("Extracting from queue: " + mhandle);

		if (this.message.get().isFieldEmpty("Payload"))
			return this.deleteMessage;

		String protectedMessage = this.message.get().getFieldAsString("Payload");

		this.message.get().removeField("Payload");

		this.chainThen(trun,
				ChainWork
						.of(DecryptPortableMessageWork.of(protectedMessage))
						.then(new IWork() {
							@Override
							public void run(TaskContext taskctx) throws OperatingContextException {
								RecordStruct pmsg = Struct.objectToRecord(taskctx.getParams());

								if (pmsg != null)
									InBoxQueuePollWork.this.message.get().with("Payload", pmsg);

								taskctx.returnEmpty();
							}
						}),
				this.handleMessage);

		return StateWorkStep.WAIT;
	}

	public StateWorkStep doHandlePayload(TaskContext trun) throws OperatingContextException {
		OperationContext.getOrThrow().touch();

		if (this.message.get() == null)
			return this.collectMessage;

		String mhandle = this.message.get().getFieldAsString("ReceiptHandle");

		if (StringUtil.isEmpty(mhandle))
			return this.collectMessage;

		Logger.info("Handling from queue: " + mhandle);

		if (this.message.get().isFieldEmpty("Payload"))
			return this.deleteMessage;

		this.chainThen(trun, PortableRequestProcessWork.of(this.message.get().getFieldAsRecord("Payload")), this.deleteMessage);

		return StateWorkStep.WAIT;
	}

	public StateWorkStep doDeleteMessage(TaskContext trun) throws OperatingContextException {
		OperationContext.getOrThrow().touch();

		if (this.message.get() == null)
			return this.collectMessage;

		String mhandle = this.message.get().getFieldAsString("ReceiptHandle");

		if (StringUtil.isEmpty(mhandle))
			return this.collectMessage;

		Logger.info("Deleting from queue: " + mhandle);

		String region = this.awssettings.getAttribute("SQSRegion");			// queue region
		String path = this.awssettings.getAttribute("InBoxQueue");			// queue path

		this.chainThen(trun, new IWork() {
			@Override
			public void run(TaskContext taskctxsch) throws OperatingContextException {
				AWSSQS.deleteMessage(InBoxQueuePollWork.this.awssettings, region, path, mhandle, new OperationOutcome<>() {
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
		}, this.collectMessage);

		return StateWorkStep.WAIT;
	}

	public StateWorkStep doFinish(TaskContext trun) throws OperatingContextException {
		Logger.info("Finish Checking SQS Queues for service messages");

		return StateWorkStep.STOP;		// needed so we don't flag "complete" on previous step
	}
}
