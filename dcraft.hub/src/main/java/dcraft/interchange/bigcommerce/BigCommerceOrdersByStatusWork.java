package dcraft.interchange.bigcommerce;

import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
import dcraft.hub.op.OperationOutcomeList;
import dcraft.log.Logger;
import dcraft.struct.ListStruct;
import dcraft.struct.RecordStruct;
import dcraft.task.StateWork;
import dcraft.task.StateWorkStep;
import dcraft.task.TaskContext;
import dcraft.util.TimeUtil;

import java.time.ZoneId;
import java.time.ZonedDateTime;

public class BigCommerceOrdersByStatusWork extends StateWork {
	static public BigCommerceOrdersByStatusWork of(int status, String settingsalt) {
		BigCommerceOrdersByStatusWork work = new BigCommerceOrdersByStatusWork();
		work.status = status;
		work.settingsalt = settingsalt;
		return work;
	}
	
	protected int status = 0;
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
		
		BigCommerceUtil.loadOrdersByStatus(this.settingsalt, this.status, currentPage, new OperationOutcomeList() {
			@Override
			public void callback(ListStruct bcresult) throws OperatingContextException {
				if (this.hasErrors()) {
					Logger.error("Cannot load orders from BC, unable to establish oldest modified order in given date range");
					BigCommerceOrdersByStatusWork.this.transition(trun, StateWorkStep.STOP);
					return;
				}
				
				orders = bcresult;		// might be null, but doesn't matter
				
				currentPage++;		// so we'll load next page if step is called again
				
				if (this.isEmptyResult())
					BigCommerceOrdersByStatusWork.this.transition(trun, collectOrderProducts);
				else
					BigCommerceOrdersByStatusWork.this.transition(trun, filterOrdersPage);
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

			// cleanup order
			order.with("date_created", ZonedDateTime.from(TimeUtil.rfc822DateFormatter.parse(order.getFieldAsString("date_created"))));
			order.with("date_modified", modified);
			
			if (order.isNotFieldEmpty("date_shipped"))
				order.with("date_shipped", ZonedDateTime.from(TimeUtil.rfc822DateFormatter.parse(order.getFieldAsString("date_shipped"))));
			
			allorders.with(order);		// TODO oldest is always first
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
		
		/*
			https://developer.bigcommerce.com/api-docs/getting-started/api-status-codes
			
			429	Too Many Requests
		
			https://developer.bigcommerce.com/api-docs/getting-started/best-practices
		
			API Rate Limits
			Apps that authenticate with OAuth are rate-limited, based on a quota that is refreshed every few seconds. The maximum quota for a store will vary depending on the store’s plan.
			
			Enterprise plans and Enterprise Sandboxes (Enterprise-Test): Unlimited (7mil / 30sec)
			Pro plans: 60k per hour (450 / 30sec)
			All other sandboxes (Dev/Partner/Employee): 20k per hour (150 / 30sec)
			Plus & Standard plans: 20k per hour (150 / 30sec)
		 */
		
		try {
			Thread.sleep(205);		// so we fall under 5 / sec
		}
		catch (InterruptedException x) {
			Logger.error("Interrupted");
			return StateWorkStep.STOP;
		}
		
		BigCommerceUtil.loadOrderProducts(settingsalt, orderid, new OperationOutcomeList() {
			@Override
			public void callback(ListStruct bcresult) throws OperatingContextException {
				if (this.hasErrors()) {
					Logger.error("Cannot load order products from BC, unable to establish oldest modified order in given date range");
					BigCommerceOrdersByStatusWork.this.transition(trun, StateWorkStep.STOP);
					return;
				}
				
				order.with("items", bcresult);

				BigCommerceUtil.loadOrderShipping(settingsalt, orderid, new OperationOutcomeList() {
					@Override
					public void callback(ListStruct bcresult) throws OperatingContextException {
						if (this.hasErrors()) {
							Logger.error("Cannot load order shipping from BC, unable to establish oldest modified order in given date range");
							BigCommerceOrdersByStatusWork.this.transition(trun, StateWorkStep.STOP);
							return;
						}

						order.with("shipping_addresses", bcresult);

						BigCommerceOrdersByStatusWork.this.transition(trun, collectOrderProducts);	// next products
					}
				});
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
