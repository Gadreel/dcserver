package dcraft.interchange.bigcommerce;

import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
import dcraft.hub.op.OperationOutcomeList;
import dcraft.hub.op.OperationOutcomeRecord;
import dcraft.log.Logger;
import dcraft.struct.ListStruct;
import dcraft.struct.RecordStruct;
import dcraft.task.StateWork;
import dcraft.task.StateWorkStep;
import dcraft.task.TaskContext;
import dcraft.util.TimeUtil;

import java.time.ZoneId;
import java.time.ZonedDateTime;

public class BigCommerceProductListWork extends StateWork {
	static public BigCommerceProductListWork of(String settingsalt) {
		BigCommerceProductListWork work = new BigCommerceProductListWork();
		work.settingsalt = settingsalt;
		return work;
	}
	
	protected String settingsalt = null;
	
	protected int currentPage = 1;   // paging starts from 1 in BC
	protected int maxPage = 1;   // zero means last page unknown
	
	protected ListStruct products = ListStruct.list();		// for current page
	
	protected StateWorkStep collectProductsPage = null;
	protected StateWorkStep results = null;
	
	@Override
	public void prepSteps(TaskContext trun) throws OperatingContextException {
		this
				.withStep(collectProductsPage = StateWorkStep.of("Collect Products Page", this::doCollectProductsPage))
				.withStep(results = StateWorkStep.of("Return Results", this::doResults));
	}
	
	public StateWorkStep doCollectProductsPage(TaskContext trun) throws OperatingContextException {
		Logger.info("Loading page: " + currentPage);
		
		if (currentPage > maxPage)
			return results;
		
		BigCommerceUtil.loadAllProducts(this.settingsalt, currentPage, new OperationOutcomeRecord() {
			@Override
			public void callback(RecordStruct bcresult) throws OperatingContextException {
				if (this.hasErrors() || this.isEmptyResult()) {
					Logger.error("Cannot load products from BC");
					BigCommerceProductListWork.this.transition(trun, StateWorkStep.STOP);
					return;
				}
				
				currentPage++;		// so we'll load next page if step is called again
				
				products.withCollection(bcresult.getFieldAsList("data"));
				
				maxPage = (int) bcresult.selectAsInteger("meta/pagination/total_pages",10);
				
				BigCommerceProductListWork.this.transition(trun, collectProductsPage);
			}
		});
		
		return StateWorkStep.WAIT;
	}
	
	public StateWorkStep doResults(TaskContext trun) throws OperatingContextException {
		Logger.info("Returning products: " + this.products.size());
		
		trun.setResult(this.products);
		
		return StateWorkStep.STOP;
	}
}
