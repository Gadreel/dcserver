package dcraft.cms.store.db.orders;

import dcraft.db.ICallContext;
import dcraft.db.proc.IStoredProc;
import dcraft.db.request.update.DbRecordRequest;
import dcraft.db.request.update.UpdateRecordRequest;
import dcraft.db.tables.TableUtil;
import dcraft.db.tables.TablesAdapter;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationOutcomeStruct;
import dcraft.log.Logger;
import dcraft.struct.RecordStruct;
import dcraft.struct.Struct;
import dcraft.util.TimeUtil;

import java.time.ZonedDateTime;

public class UpdateStatus implements IStoredProc {
	@Override
	public void execute(ICallContext request, OperationOutcomeStruct callback) throws OperatingContextException {
		RecordStruct data = request.getDataAsRecord();
		
		TablesAdapter db = TablesAdapter.of(request);

		String id = data.getFieldAsString("Id");
		String status = data.getFieldAsString("Status");

		if (! db.isCurrent("dcmOrder", id)) {
			Logger.error("Order not found: " + id);
			callback.returnEmpty();
			return;
		}

		ZonedDateTime stamp = TimeUtil.now();

		RecordStruct audit = RecordStruct.record()
				.with("Origin", "Store")
				.with("Stamp", stamp)
				.with("Internal", false)
				.with("Comment", "Status updated")
				.with("Status", status);

		DbRecordRequest upreq = UpdateRecordRequest.update()
				.withTable("dcmOrder")
				.withId(id)
				.withSetField("dcmAudit", TimeUtil.stampFmt.format(stamp), audit)
				.withUpdateField("dcmStatus", status);

		TableUtil.updateRecord(db, upreq);

		if ("Completed".equals(status) || "Canceled".equals(status)) {
			// complete or cancel all items as well
			for (String iid : db.getListKeys("dcmOrder", id, "dcmItemEntryId")) {
				String istatus = Struct.objectToString(db.getList("dcmOrder", id, "dcmItemStatus", iid));

				if (! "Completed".equals(istatus) && ! "Canceled".equals(istatus)) {
					db.setList("dcmOrder", id, "dcmItemStatus", iid, status);
					db.setList("dcmOrder", id, "dcmItemUpdated", iid, stamp);
				}
			}
		}

		dcraft.cms.store.db.Util.updateOrderStatus(db, id);

		callback.returnEmpty();
	}
}
