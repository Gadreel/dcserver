package dcraft.cms.store;

import dcraft.cms.store.db.Util;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationOutcomeStruct;
import dcraft.log.Logger;
import dcraft.service.ServiceHub;
import dcraft.service.ServiceRequest;
import dcraft.struct.BaseStruct;
import dcraft.struct.Struct;
import dcraft.task.ChainWork;
import dcraft.task.IWork;
import dcraft.task.TaskContext;
import z.apss.work.ProcessOrders;

public class CheckDiscountsBatch extends ChainWork {
	@Override
	protected void init(TaskContext taskctx) throws OperatingContextException {
		Logger.info("Start Store Discount BATCH");

		this
				.then(new IWork() {
					@Override
					public void run(TaskContext taskctx) throws OperatingContextException {
						ServiceHub.call(ServiceRequest.of("dcmStoreServices.Discounts.ScheduleAll").withOutcome(new OperationOutcomeStruct() {
							@Override
							public void callback(BaseStruct result) throws OperatingContextException {
								taskctx.returnEmpty();
							}
						}));
					}
				})
				.then(new IWork() {
					@Override
					public void run(TaskContext taskctx) throws OperatingContextException {
						Logger.info("End Store Discount BATCH");
						
						taskctx.complete();
					}
				})
		;
	}
}
