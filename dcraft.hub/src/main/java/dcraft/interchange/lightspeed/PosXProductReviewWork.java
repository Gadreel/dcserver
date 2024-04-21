package dcraft.interchange.lightspeed;

import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
import dcraft.hub.op.OperationOutcomeRecord;
import dcraft.log.Logger;
import dcraft.struct.ListStruct;
import dcraft.struct.RecordStruct;
import dcraft.task.IWork;
import dcraft.task.StateWork;
import dcraft.task.StateWorkStep;
import dcraft.task.TaskContext;
import dcraft.util.StringUtil;

import java.util.ArrayDeque;
import java.util.Deque;

public class PosXProductReviewWork extends StateWork {
	static public PosXProductReviewWork of(String lastversion, String settingsalt, IWork reviewer) {
		PosXProductReviewWork work = new PosXProductReviewWork();
		work.lastversion = lastversion;
		work.settingsalt = settingsalt;
		work.reviewer = reviewer;
		return work;
	}
	
	protected String lastversion = null;
	protected String settingsalt = null;
	protected IWork reviewer = null;

	protected int reviewedCnt = 0;
	protected int currentOffset = 0;
	protected boolean runonce = false;

	protected Deque<RecordStruct> items = new ArrayDeque<>();	// for current page

	protected StateWorkStep collectNextPage = null;
	protected StateWorkStep consumeProduct = null;
	protected StateWorkStep finish = null;

	public String getLastVersion() {
		return this.lastversion;
	}

	@Override
	public void prepSteps(TaskContext trun) throws OperatingContextException {
		this
				.withStep(collectNextPage = StateWorkStep.of("Collect Products Page", this::doCollectProducts))
				.withStep(consumeProduct = StateWorkStep.of("Process one product", this::doReviewProduct))
				.withStep(finish = StateWorkStep.of("Return", this::doFinish));
	}

	public StateWorkStep doCollectProducts(TaskContext trun) throws OperatingContextException {
		Logger.info("Loading page: " + this.currentOffset);

		System.out.println(" - page " + this.currentOffset +  " - reviewed " + reviewedCnt);

		try {
			// don't overrun the rate
			if (this.runonce)
				Thread.sleep(200L);
		}
		catch (InterruptedException x) {
		}

		this.runonce = true;

		OperationOutcomeRecord outcomeRecord = new OperationOutcomeRecord() {
			@Override
			public void callback(RecordStruct result) throws OperatingContextException {
				currentOffset++;

				if (this.hasErrors()) {
					Logger.error("Cannot load products");
					PosXProductReviewWork.this.transition(trun, StateWorkStep.STOP);
					return;
				}

				String maxver = result.selectAsString("version.max");

				if (StringUtil.isNotEmpty(maxver))
					lastversion = maxver;

				ListStruct list = result.getFieldAsList("data");

				if (list != null) {
					if (list.size() == 0) {
						Logger.info("Finished loading pr0" +
								"oducts");
						PosXProductReviewWork.this.transition(trun, StateWorkStep.STOP);
						return;
					}

					for (int i = 0; i < list.size(); i++) {
						RecordStruct item = list.getItemAsRecord(i);

						System.out.println("found " + item.getFieldAsString("name"));

						items.addLast(item);
					}
				}

				PosXProductReviewWork.this.transition(trun, consumeProduct);
			}
		};

		PosXUtil.productList(this.settingsalt, this.lastversion, outcomeRecord);

		return StateWorkStep.WAIT;
	}
	
	public StateWorkStep doReviewProduct(TaskContext trun) throws OperatingContextException {
		OperationContext.getOrThrow().touch();

		RecordStruct item = this.items.pollFirst();

		if (item == null)
			return this.collectNextPage;

		this.reviewedCnt++;

		//Logger.info("Reviewing product: " + item.getFieldAsString("name") + " : " + item.getFieldAsString("id"));

		if (this.reviewer == null)
			return StateWorkStep.REPEAT;

		trun.setResult(item);

		this.chainThenRepeat(trun, this.reviewer);

		return StateWorkStep.WAIT;
	}

	public StateWorkStep doFinish(TaskContext trun) throws OperatingContextException {
		Logger.info("Product review complete");

		System.out.println(" - page " + currentOffset + " - reviewed " + reviewedCnt);

		return StateWorkStep.STOP;
	}
}
