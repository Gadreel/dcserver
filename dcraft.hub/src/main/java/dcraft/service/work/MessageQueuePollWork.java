package dcraft.service.work;

import dcraft.hub.app.ApplicationHub;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
import dcraft.hub.op.OperationMarker;
import dcraft.hub.op.OperationOutcome;
import dcraft.interchange.aws.AWSSQS;
import dcraft.log.Logger;
import dcraft.struct.ListStruct;
import dcraft.struct.RecordStruct;
import dcraft.struct.Struct;
import dcraft.task.*;
import dcraft.util.StringUtil;
import dcraft.xml.XElement;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

abstract public class MessageQueuePollWork extends StateWork {
	protected AtomicLong callcount = new AtomicLong();
	protected AtomicBoolean stopPolling = new AtomicBoolean(false);

	protected StateWorkStep doInit = null;
	protected StateWorkStep collectMessage = null;
	protected StateWorkStep extractMessage = null;
	protected StateWorkStep handleMessage = null;
	protected StateWorkStep cleanMessage = null;
	protected StateWorkStep finish = null;

	@Override
	public void prepSteps(TaskContext trun) throws OperatingContextException {
		this
				.withStep(this.doInit = StateWorkStep.of("Init", this::doInit))
				.withStep(this.collectMessage = StateWorkStep.of("Collect Queue Message", this::doCollectMessages))
				.withStep(this.extractMessage = StateWorkStep.of("Extract Payload Message", this::doExtractPayload))
				.withStep(this.handleMessage = StateWorkStep.of("Handle Payload Message", this::doHandlePayload))
				.withStep(this.cleanMessage = StateWorkStep.of("Delete Queue Message", this::doCleanMessage))
				.withStep(this.finish = StateWorkStep.of("Finish", this::doFinish));
	}

	abstract public StateWorkStep doInit(TaskContext trun) throws OperatingContextException;

	abstract public StateWorkStep doCollectMessages(TaskContext trun) throws OperatingContextException;

	abstract public StateWorkStep doExtractPayload(TaskContext trun) throws OperatingContextException;

	abstract public StateWorkStep doHandlePayload(TaskContext trun) throws OperatingContextException;

	abstract public StateWorkStep doCleanMessage(TaskContext trun) throws OperatingContextException;

	abstract public StateWorkStep doFinish(TaskContext trun) throws OperatingContextException;
}
