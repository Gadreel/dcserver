package dcraft.cms.store.db.orders;

import dcraft.db.ICallContext;
import dcraft.db.proc.IStoredProc;
import dcraft.db.tables.TablesAdapter;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationOutcomeStruct;
import dcraft.log.Logger;
import dcraft.struct.ListStruct;
import dcraft.struct.RecordStruct;
import dcraft.struct.Struct;

import java.math.BigDecimal;

public class CalcShipWeight implements IStoredProc {
	@Override
	public void execute(ICallContext request, OperationOutcomeStruct callback) throws OperatingContextException {
		RecordStruct data = request.getDataAsRecord();
		
		TablesAdapter db = TablesAdapter.of(request);

		String id = data.getFieldAsString("Id");
		ListStruct items = data.getFieldAsList("Items");

		if (! db.isCurrent("dcmOrder", id)) {
			Logger.error("Order not found: " + id);
			callback.returnEmpty();
			return;
		}

		// the default dimensions can be calculated
		RecordStruct ret = RecordStruct.record()
				.with("Width", 12)
				.with("Height", 12)
				.with("Depth", 6);

		BigDecimal totalweight = BigDecimal.ZERO;

		for (int i = 0; i < items.size(); i++) {
			String iid = items.getItemAsString(i);
			
			String pid = Struct.objectToString(db.getList("dcmOrder", id, "dcmItemProduct", iid));

			BigDecimal weight = Struct.objectToDecimal(db.getScalar("dcmProduct", pid,"dcmShipWeight"));

			if (weight != null) {
				long qty = Struct.objectToInteger(db.getList("dcmOrder", id, "dcmItemQuantity", iid));

				weight = weight.multiply(BigDecimal.valueOf(qty));

				totalweight = totalweight.add(weight);
			}
		}

		ret.with("Weight", totalweight);

		callback.returnValue(ret);
	}
}
