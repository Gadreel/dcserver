package dcraft.cms.store.db.orders;

import dcraft.db.ICallContext;
import dcraft.db.proc.IStoredProc;
import dcraft.db.request.update.DbRecordRequest;
import dcraft.db.request.update.UpdateRecordRequest;
import dcraft.db.tables.TableUtil;
import dcraft.db.tables.TablesAdapter;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationOutcomeStruct;
import dcraft.struct.RecordStruct;

public class Update implements IStoredProc {
	@Override
	public void execute(ICallContext request, OperationOutcomeStruct callback) throws OperatingContextException {
		RecordStruct data = request.getDataAsRecord();

		TablesAdapter db = TablesAdapter.of(request);

		DbRecordRequest upreq = UpdateRecordRequest.update()
				.withId(data.getFieldAsString("Id"))
				.withTable("dcmOrder")
				.withConditionallyUpdateFields(data,
						"CustomerInfo", "dcmCustomerInfo", "ShippingInfo", "dcmShippingInfo",
						"Comment", "dcmComment", "Delivery", "dcmDelivery"
				);

		TableUtil.updateRecord(db, upreq);

		callback.returnEmpty();
	}
}
