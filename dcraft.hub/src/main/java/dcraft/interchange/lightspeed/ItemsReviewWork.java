package dcraft.interchange.lightspeed;

import dcraft.hub.op.*;
import dcraft.interchange.bigcommerce.BigCommerceUtil;
import dcraft.interchange.moodle.MoodleUtil;
import dcraft.log.Logger;
import dcraft.struct.ListStruct;
import dcraft.struct.RecordStruct;
import dcraft.task.IWork;
import dcraft.task.StateWork;
import dcraft.task.StateWorkStep;
import dcraft.task.TaskContext;
import dcraft.util.StringUtil;
import dcraft.util.TimeUtil;
import org.apache.lucene.util.ThreadInterruptedException;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayDeque;
import java.util.Deque;

public class ItemsReviewWork extends StateWork {
	static public ItemsReviewWork of(ZonedDateTime lastupdate, String settingsalt, String query, IWork reviewer) {
		ItemsReviewWork work = new ItemsReviewWork();
		work.lastsync = (lastupdate != null) ? lastupdate : ZonedDateTime.of(1970, 1, 1, 0, 0, 0, 0, ZoneId.of("UTC"));
		work.settingsalt = settingsalt;
		work.reviewer = reviewer;
		work.query = query;
		return work;
	}
	
	protected ZonedDateTime lastsync = null;
	protected String settingsalt = null;
	protected String query = null;
	protected IWork reviewer = null;
	protected String access = null;
	protected ListStruct relations = ListStruct.list("ItemShops","ItemPrices","TagRelations");

	protected int currentOffset = 0;   // paging starts from 0 in LS
	protected int totalCnt = -1;
	protected int reviewedCnt = 0;

	protected Deque<RecordStruct> items = new ArrayDeque<>();	// for current page

	protected StateWorkStep accessToken = null;
	protected StateWorkStep collectNextPage = null;
	protected StateWorkStep consumeItem = null;
	protected StateWorkStep finish = null;

	public String getAccessToken() {
		return this.access;
	}

	@Override
	public void prepSteps(TaskContext trun) throws OperatingContextException {
		this
				.withStep(accessToken = StateWorkStep.of("Get an access token", this::doCollectAccess))
				.withStep(collectNextPage = StateWorkStep.of("Collect Items Page", this::doCollectItems))
				.withStep(consumeItem = StateWorkStep.of("Process one item", this::doReviewItem))
				.withStep(finish = StateWorkStep.of("Return", this::doFinish));
	}
	
	public StateWorkStep doCollectAccess(TaskContext trun) throws OperatingContextException {
		if (StringUtil.isNotEmpty(this.access))
			return collectNextPage;

		LightspeedRetailUtil.getAccessToken(this.settingsalt, new OperationOutcomeString() {
			@Override
			public void callback(String result) throws OperatingContextException {
				if (this.hasErrors()) {
					Logger.error("Cannot access token");
					ItemsReviewWork.this.transition(trun, StateWorkStep.STOP);
					return;
				}

				access = result;

				System.out.println("access: " + access);

				ItemsReviewWork.this.transition(trun, collectNextPage);
			}
		});

		return StateWorkStep.WAIT;
	}

	public StateWorkStep doCollectItems(TaskContext trun) throws OperatingContextException {
		Logger.info("Loading page: " + this.currentOffset);

		System.out.println(" - total " + totalCnt + " - reviewed " + reviewedCnt);

		// always load at least once, but don't load if we've seen all the items
		if ((this.totalCnt != -1)  && (this.reviewedCnt >= this.totalCnt))
			return finish;

		try {
			// don't overrun the rate
			Thread.sleep(1000L);
		}
		catch (InterruptedException x) {
		}

		LightspeedRetailUtil.itemList(this.settingsalt, this.access, this.relations, this.currentOffset, this.query, new OperationOutcomeRecord() {
			@Override
			public void callback(RecordStruct result) throws OperatingContextException {
				currentOffset++;

				if (this.hasErrors()) {
					Logger.error("Cannot load items");
					ItemsReviewWork.this.transition(trun, StateWorkStep.STOP);
					return;
				}

				// first load get the total count of items for this query
				if (totalCnt == -1) {
					RecordStruct meta = result.getFieldAsRecord("@attributes");

					if (meta != null)
						totalCnt = (int)meta.getFieldAsInteger("count", 0);
				}

				ListStruct list = result.getFieldAsList("Item");

				if (list != null) {
					for (int i = 0; i < list.size(); i++) {
						RecordStruct item = list.getItemAsRecord(i);

						System.out.println("found " + item.getFieldAsString("description"));

						items.addLast(item);
					}
				}

				RecordStruct singleitem = result.getFieldAsRecord("Item");

				if (singleitem != null) {
					System.out.println("adding " + singleitem.getFieldAsString("description"));

					items.addLast(singleitem);
				}

				ItemsReviewWork.this.transition(trun, consumeItem);
			}
		});

		return StateWorkStep.WAIT;
	}
	
	public StateWorkStep doReviewItem(TaskContext trun) throws OperatingContextException {
		OperationContext.getOrThrow().touch();

		RecordStruct item = this.items.pollFirst();

		if (item == null)
			return this.collectNextPage;

		this.reviewedCnt++;

		Logger.info("Reviewing item: " + item.getFieldAsString("description") + " : " + item.getFieldAsString("itemID"));

		if (this.reviewer == null)
			return StateWorkStep.REPEAT;

		trun.setResult(item);

		this.chainThenRepeat(trun, this.reviewer);

		return StateWorkStep.WAIT;
	}

	public StateWorkStep doFinish(TaskContext trun) throws OperatingContextException {
		Logger.info("Item review complete");

		System.out.println(" - total " + totalCnt + " - reviewed " + reviewedCnt);

		return StateWorkStep.STOP;
	}
}
