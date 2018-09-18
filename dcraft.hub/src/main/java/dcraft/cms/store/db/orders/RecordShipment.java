package dcraft.cms.store.db.orders;

import dcraft.db.DatabaseAdapter;
import dcraft.db.DatabaseException;
import dcraft.db.ICallContext;
import dcraft.db.proc.IStoredProc;
import dcraft.db.tables.TablesAdapter;
import dcraft.filestore.CommonPath;
import dcraft.filestore.FileStore;
import dcraft.filestore.local.LocalStore;
import dcraft.filevault.Vault;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
import dcraft.hub.op.OperationOutcomeStruct;
import dcraft.log.Logger;
import dcraft.struct.ListStruct;
import dcraft.struct.RecordStruct;
import dcraft.struct.Struct;
import dcraft.util.StringUtil;
import dcraft.util.TimeUtil;
import dcraft.xml.XElement;
import dcraft.xml.XmlReader;

import java.time.ZonedDateTime;
import java.util.List;

public class RecordShipment implements IStoredProc {
	@Override
	public void execute(ICallContext request, OperationOutcomeStruct callback) throws OperatingContextException {
		RecordStruct data = request.getDataAsRecord();
		
		TablesAdapter db = TablesAdapter.ofNow(request);

		String id = data.getFieldAsString("Id");
		RecordStruct shmp = data.getFieldAsRecord("Shipment");
		ListStruct items = data.getFieldAsList("Items");

		if (! db.isCurrent("dcmOrder", id)) {
			Logger.error("Order not found: " + id);
			callback.returnEmpty();
			return;
		}
		
		String eid = shmp.getFieldAsString("EntryId");
		
		db.setStaticList("dcmOrder", id, "dcmShipmentInfo", eid, shmp);
		
		ZonedDateTime now = TimeUtil.now();
		
		for (int i = 0; i < items.size(); i++) {
			String iid = items.getItemAsString(i);
			
			db.setStaticList("dcmOrder", id, "dcmItemShipment", iid, eid);
			db.setStaticList("dcmOrder", id, "dcmItemStatus", iid, "Completed");
			db.setStaticList("dcmOrder", id, "dcmItemUpdated", iid, now);
		}
		
		dcraft.cms.store.db.Util.updateOrderStatus(db, id);

		callback.returnEmpty();
	}
}
