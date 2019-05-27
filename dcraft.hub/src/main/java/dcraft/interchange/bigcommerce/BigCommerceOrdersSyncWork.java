package dcraft.interchange.bigcommerce;

import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
import dcraft.hub.op.OperationOutcomeList;
import dcraft.log.Logger;
import dcraft.struct.ListStruct;
import dcraft.struct.RecordStruct;
import dcraft.struct.Struct;
import dcraft.task.StateWork;
import dcraft.task.StateWorkStep;
import dcraft.task.TaskContext;
import dcraft.util.TimeUtil;

import java.time.ZoneId;
import java.time.ZonedDateTime;

public class BigCommerceOrdersSyncWork extends StateWork {
	static public BigCommerceOrdersSyncWork of(ZonedDateTime lastupdate, String settingsalt) {
		BigCommerceOrdersSyncWork work = new BigCommerceOrdersSyncWork();
		work.lastsync = (lastupdate != null) ? lastupdate : ZonedDateTime.of(1970, 1, 1, 0, 0, 0, 0, ZoneId.of("UTC"));
		work.settingsalt = settingsalt;
		return work;
	}
	
	protected ZonedDateTime lastsync = null;
	protected String settingsalt = null;
	
	protected ListStruct allorders = ListStruct.list();
	protected int currentOrder = 0;
	
	protected int currentPage = 1;   // paging starts from 1 in BC
	
	protected ListStruct orders = null;		// for current page
	
	protected StateWorkStep collectOrdersPage = null;
	protected StateWorkStep filterOrdersPage = null;
	protected StateWorkStep collectOrderProducts = null;
	protected StateWorkStep results = null;
	
	@Override
	public void prepSteps(TaskContext trun) throws OperatingContextException {
		this
				.withStep(collectOrdersPage = StateWorkStep.of("Collect Orders Page", this::doCollectOrdersPage))
				.withStep(filterOrdersPage = StateWorkStep.of("Filter Orders Page", this::doFilterOrdersPage))
				.withStep(collectOrderProducts = StateWorkStep.of("Collect Order Products", this::doCollectOrderProducts))
				.withStep(results = StateWorkStep.of("Return Results", this::doResults));
	}
	
	public StateWorkStep doCollectOrdersPage(TaskContext trun) throws OperatingContextException {
		Logger.info("Loading page: " + currentPage);
		
		orders = null;
		
		BigCommerceUtil.loadModifiedOrders(this.settingsalt, currentPage, new OperationOutcomeList() {
			@Override
			public void callback(ListStruct bcresult) throws OperatingContextException {
				if (this.hasErrors()) {
					Logger.error("Cannot load orders from BC, unable to establish oldest modified order in given date range");
					BigCommerceOrdersSyncWork.this.transition(trun, StateWorkStep.STOP);
					return;
				}
				
				orders = bcresult;		// might be null, but doesn't matter
				
				currentPage++;		// so we'll load next page if step is called again
				
				if (this.isEmptyResult())
					BigCommerceOrdersSyncWork.this.transition(trun, collectOrderProducts);
				else
					BigCommerceOrdersSyncWork.this.transition(trun, filterOrdersPage);
			}
		});
		
		return StateWorkStep.WAIT;
	}
	
	public StateWorkStep doFilterOrdersPage(TaskContext trun) throws OperatingContextException {
		OperationContext.getOrThrow().touch();

		if (orders == null)
			return collectOrderProducts;
		
		Logger.info("Filtering orders: " + orders.size());
		
		for (int i = 0; i < orders.size(); i++) {
			RecordStruct order = orders.getItemAsRecord(i);

			ZonedDateTime modified = ZonedDateTime.from(TimeUtil.rfc822DateFormatter.parse(order.getFieldAsString("date_modified")));
			
			// this check will let some repeat orders through, fine because caller needs to be able to handle repeats/updates
			if (modified.isBefore(lastsync))
				return collectOrderProducts;
			
			// cleanup order
			order.with("date_created", ZonedDateTime.from(TimeUtil.rfc822DateFormatter.parse(order.getFieldAsString("date_created"))));
			order.with("date_modified", modified);
			
			if (order.isNotFieldEmpty("date_shipped"))
				order.with("date_shipped", ZonedDateTime.from(TimeUtil.rfc822DateFormatter.parse(order.getFieldAsString("date_shipped"))));
			
			allorders.addItem(0, order);		// oldest is always first
		}
		
		return collectOrdersPage;
	}
	
	public StateWorkStep doCollectOrderProducts(TaskContext trun) throws OperatingContextException {
		RecordStruct order = allorders.getItemAsRecord(currentOrder);
		
		// nothing left
		if (order == null)
			return results;

		currentOrder++;
		
		Long orderid = order.getFieldAsInteger("id");
		
		if (orderid == null)
			return StateWorkStep.REPEAT;		// next
		
		Logger.info("Loading order items for: " + orderid);
		
		BigCommerceUtil.loadOrderProducts(settingsalt, orderid, new OperationOutcomeList() {
			@Override
			public void callback(ListStruct bcresult) throws OperatingContextException {
				if (this.hasErrors()) {
					Logger.error("Cannot load order products from BC, unable to establish oldest modified order in given date range");
					BigCommerceOrdersSyncWork.this.transition(trun, StateWorkStep.STOP);
					return;
				}
				
				order.with("items", bcresult);
				
				BigCommerceOrdersSyncWork.this.transition(trun, collectOrderProducts);	// next products
			}
		});
		
		return StateWorkStep.WAIT;
	}
	
	public StateWorkStep doResults(TaskContext trun) throws OperatingContextException {
		Logger.info("Returning orders: " + this.allorders.size());
		
		trun.setResult(this.allorders);
		
		return StateWorkStep.STOP;
	}
}
