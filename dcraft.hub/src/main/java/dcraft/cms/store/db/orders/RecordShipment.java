package dcraft.cms.store.db.orders;

import dcraft.db.ICallContext;
import dcraft.db.proc.IStoredProc;
import dcraft.db.tables.TablesAdapter;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationOutcomeStruct;
import dcraft.log.Logger;
import dcraft.struct.ListStruct;
import dcraft.struct.RecordStruct;
import dcraft.util.TimeUtil;

import java.time.ZonedDateTime;

public class RecordShipment implements IStoredProc {
	@Override
	public void execute(ICallContext request, OperationOutcomeStruct callback) throws OperatingContextException {
		RecordStruct data = request.getDataAsRecord();
		
		TablesAdapter db = TablesAdapter.of(request);

		String id = data.getFieldAsString("Id");
		RecordStruct shmp = data.getFieldAsRecord("Shipment");
		ListStruct items = data.getFieldAsList("Items");

		if (! db.isCurrent("dcmOrder", id)) {
			Logger.error("Order not found: " + id);
			callback.returnEmpty();
			return;
		}
		
		String eid = shmp.getFieldAsString("EntryId");
		
		db.setList("dcmOrder", id, "dcmShipmentInfo", eid, shmp);
		
		ZonedDateTime now = TimeUtil.now();
		
		for (int i = 0; i < items.size(); i++) {
			String iid = items.getItemAsString(i);
			
			db.setList("dcmOrder", id, "dcmItemShipment", iid, eid);
			db.setList("dcmOrder", id, "dcmItemStatus", iid, "Completed");
			db.setList("dcmOrder", id, "dcmItemUpdated", iid, now);
		}
		
		dcraft.cms.store.db.Util.updateOrderStatus(db, id);

		callback.returnEmpty();
	}
}
