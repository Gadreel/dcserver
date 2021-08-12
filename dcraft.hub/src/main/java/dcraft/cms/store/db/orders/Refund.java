package dcraft.cms.store.db.orders;

import dcraft.cms.store.OrderUtil;
import dcraft.db.ICallContext;
import dcraft.db.proc.IStoredProc;
import dcraft.db.request.update.DbRecordRequest;
import dcraft.db.request.update.UpdateRecordRequest;
import dcraft.db.tables.TableUtil;
import dcraft.db.tables.TablesAdapter;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationOutcomeString;
import dcraft.hub.op.OperationOutcomeStruct;
import dcraft.log.Logger;
import dcraft.struct.RecordStruct;
import dcraft.struct.Struct;
import dcraft.util.TimeUtil;

import java.math.BigDecimal;
import java.time.ZonedDateTime;

public class Refund implements IStoredProc {
	@Override
	public void execute(ICallContext request, OperationOutcomeStruct callback) throws OperatingContextException {
		RecordStruct data = request.getDataAsRecord();

		TablesAdapter db = TablesAdapter.of(request);

		String id = data.getFieldAsString("Id");
		boolean cancel = data.getFieldAsBooleanOrFalse("Cancel");
		BigDecimal amount = data.getFieldAsDecimal("Amount");

		if (! db.isCurrent("dcmOrder", id)) {
			Logger.error("Order not found: " + id);
			callback.returnEmpty();
			return;
		}

		ZonedDateTime stamp = TimeUtil.now();

		OrderUtil.processRefund(request, db, id, amount, new OperationOutcomeString() {
			@Override
			public void callback(String result) throws OperatingContextException {
				if (! this.hasErrors() && cancel) {
					DbRecordRequest upreq = UpdateRecordRequest.update()
							.withTable("dcmOrder")
							.withId(id)
							.withUpdateField("dcmStatus", "Canceled");

					// complete or cancel all items as well
					for (String iid : db.getListKeys("dcmOrder", id, "dcmItemEntryId")) {
						String istatus = Struct.objectToString(db.getList("dcmOrder", id, "dcmItemStatus", iid));

						if (!"Completed".equals(istatus) && !"Canceled".equals(istatus)) {
							upreq
									.withUpdateField( "dcmItemStatus", iid, "Canceled")
									.withUpdateField("dcmItemUpdated", iid, stamp);
						}
					}

					TableUtil.updateRecord(db, upreq);

					dcraft.cms.store.db.Util.updateOrderStatus(db, id);
				}

				callback.returnEmpty();
			}
		});
	}
}
