package dcraft.service.work;

import dcraft.hub.app.ApplicationHub;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
import dcraft.hub.op.OperationMarker;
import dcraft.hub.op.OperationOutcome;
import dcraft.interchange.aws.AWSSQS;
import dcraft.log.Logger;
import dcraft.service.ServiceHub;
import dcraft.struct.*;
import dcraft.task.*;
import dcraft.util.IOUtil;
import dcraft.util.StringUtil;
import dcraft.xml.XElement;

import java.io.IOException;
import java.nio.file.CopyOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.stream.Stream;

public class FileQueuePollWork extends MessageQueuePollWork {
	static public FileQueuePollWork ofOnce() {
		FileQueuePollWork work = new FileQueuePollWork();
		work.withoutFailOnErrors();		// don't fail, go to next step
		return work;
	}

	static public FileQueuePollWork ofLimit(int count) {
		FileQueuePollWork work = new FileQueuePollWork();
		work.withoutFailOnErrors();		// don't fail, go to next step
		work.limit = count;   // run no more than `count` times
		return work;
	}

	static public FileQueuePollWork ofAll() {
		FileQueuePollWork work = new FileQueuePollWork();
		work.withoutFailOnErrors();		// don't fail, go to next step
		work.all = true;   // run all, even if new get added during the process
		return work;
	}

	protected AtomicReference<CharSequence> protectedmessage = new AtomicReference<>();
	protected AtomicReference<RecordStruct> message = new AtomicReference<>();

	protected Path currenttrigger = null;
	protected int limit = 1;
	protected boolean all = false;

	@Override
	public StateWorkStep doInit(TaskContext trun) throws OperatingContextException {
		OperationContext.getOrThrow().touch();

		Logger.info("Start Checking File Triggers for service messages");

		Path trigpath = ServiceHub.MessageStore.resolvePath("/triggers");
		Path finpath = ServiceHub.MessageStore.resolvePath("/finished");

		try {
			if (Files.notExists(trigpath))
				Files.createDirectories(trigpath);

			if (Files.notExists(finpath))
				Files.createDirectories(finpath);

			return this.collectMessage;
		}
		catch (IOException x) {
			Logger.error("Unable to access message triggers folder");
		}

		return this.finish;
	}

	@Override
	public StateWorkStep doCollectMessages(TaskContext trun) throws OperatingContextException {
		OperationContext.getOrThrow().touch();

		if (this.stopPolling.get())
			return this.finish;

		// grab no more than 100 per run
		if (! this.all && (this.callcount.get() >= this.limit))
			return this.finish;

		Logger.debug("Polling my Message Trigger queue: ");

		this.protectedmessage.set(null);
		this.message.set(null);
		this.currenttrigger = null;
		this.callcount.incrementAndGet();

		try (OperationMarker om = OperationMarker.create()) {
			Path trigpath = ServiceHub.MessageStore.resolvePath("/triggers");

			List<Path> files = new ArrayList<>();

			try (Stream<Path> pathStream = Files.list(trigpath)) {
				pathStream.forEach(new Consumer<Path>() {
					@Override
					public void accept(Path path) {
						files.add(path);;
					}
				});
			}
			catch (Exception x) {
				Logger.error("Unable to list message triggers folder");
			}
			finally {
				// if errors then continue - don't let a bad sig scare us (or stop our queue progress)
				if (om.hasErrors())
					trun.clearExitCode();
			}

			if (files.size() > 0) {
				Collections.sort(files);

				Path nextMessagePath = files.get(0);

				if (nextMessagePath != null) {
					CharSequence message = IOUtil.readEntireFile(nextMessagePath);

					// do only one - should only be one
					if (StringUtil.isNotEmpty(message)) {
						this.protectedmessage.set(message);
						this.currenttrigger = nextMessagePath;
					}
				}

				return this.extractMessage;
			}
		}

		return this.finish;
	}

	@Override
	public StateWorkStep doExtractPayload(TaskContext trun) throws OperatingContextException {
		OperationContext.getOrThrow().touch();

		if (this.protectedmessage.get() == null)
			return this.collectMessage;

		CharSequence protectedMessage = this.protectedmessage.get();

		this.protectedmessage.set(null);

		this.chainThen(trun,
				ChainWork
						.of(DecryptPortableMessageWork.of(protectedMessage))
						.then(new IWork() {
							@Override
							public void run(TaskContext taskctx) throws OperatingContextException {
								RecordStruct pmsg = Struct.objectToRecord(taskctx.getParams());

								FileQueuePollWork.this.message.set(pmsg);

								taskctx.returnEmpty();
							}
						}),
				this.handleMessage);

		return StateWorkStep.WAIT;
	}

	@Override
	public StateWorkStep doHandlePayload(TaskContext trun) throws OperatingContextException {
		OperationContext.getOrThrow().touch();

		if (this.message.get() == null)
			return this.collectMessage;

		Logger.info("Handling from message queue: " + this.message.get().selectAsString("Payload.Op"));

		this.chainThen(trun, PortableRequestProcessWork.of(this.message.get()), this.cleanMessage);

		return StateWorkStep.WAIT;
	}

	@Override
	public StateWorkStep doCleanMessage(TaskContext trun) throws OperatingContextException {
		OperationContext.getOrThrow().touch();

		if (this.currenttrigger != null) {
			Logger.info("Removing from queue: " + this.currenttrigger);

			Path finpath = ServiceHub.MessageStore.resolvePath("/finished");
			Path dest = finpath.resolve(this.currenttrigger.getFileName());

			try {
				Files.move(this.currenttrigger, dest, StandardCopyOption.ATOMIC_MOVE);
			}
			catch (IOException x) {
				Logger.warn("Unable to move message from trigger queue");
			}
		}

		return this.collectMessage;
	}

	@Override
	public StateWorkStep doFinish(TaskContext trun) throws OperatingContextException {
		Logger.debug("Finish Checking File Triggers for service messages");

		return StateWorkStep.STOP;		// needed so we don't flag "complete" on previous step
	}
}
