package dcraft.cms.store.db.orders;

import dcraft.cms.store.OrderUtil;
import dcraft.db.ICallContext;
import dcraft.db.proc.IStoredProc;
import dcraft.db.request.query.SelectFields;
import dcraft.db.request.update.DbRecordRequest;
import dcraft.db.request.update.UpdateRecordRequest;
import dcraft.db.tables.TableUtil;
import dcraft.db.tables.TablesAdapter;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
import dcraft.hub.op.OperationOutcomeStruct;
import dcraft.log.Logger;
import dcraft.struct.FieldStruct;
import dcraft.struct.ListStruct;
import dcraft.struct.RecordStruct;
import dcraft.struct.Struct;
import dcraft.struct.scalar.BooleanStruct;
import dcraft.struct.scalar.StringStruct;
import dcraft.util.StringUtil;

import java.math.BigDecimal;

public class Update implements IStoredProc {
	@Override
	public void execute(ICallContext request, OperationOutcomeStruct callback) throws OperatingContextException {
		RecordStruct data = request.getDataAsRecord();

		TablesAdapter db = TablesAdapter.ofNow(request);

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
