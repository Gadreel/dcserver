package dcraft.cms.store.db.products;

import dcraft.cms.store.db.Util;
import dcraft.db.ICallContext;
import dcraft.db.proc.IStoredProc;
import dcraft.db.request.update.DbRecordRequest;
import dcraft.db.request.update.UpdateRecordRequest;
import dcraft.db.tables.TableUtil;
import dcraft.db.tables.TablesAdapter;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationMarker;
import dcraft.hub.op.OperationOutcomeStruct;
import dcraft.service.ServiceHub;
import dcraft.struct.RecordStruct;

public class Update implements IStoredProc {
	@Override
	public void execute(ICallContext request, OperationOutcomeStruct callback) throws OperatingContextException {
		RecordStruct data = request.getDataAsRecord();

		TablesAdapter db = TablesAdapter.of(request);

		try (OperationMarker om = OperationMarker.create()) {
			String locale = data.getFieldAsString("TrLocale");

			DbRecordRequest req = UpdateRecordRequest.update()
					.withTable("dcmProduct")
					.withId(data.getFieldAsString("Id"))
					.withConditionallyUpdateFields(data, "Alias", "dcmAlias", "Sku", "dcmSku", "Image", "dcmImage",
							"Price", "dcmPrice", "ShipAmount", "dcmShipAmount", "ShipWeight", "dcmShipWeight",
							"VariablePrice", "dcmVariablePrice", "MinimumPrice", "dcmMinimumPrice", "HideGeneral", "dcmHideGeneral",
							"ShipCost", "dcmShipCost", "TaxFree", "dcmTaxFree", "ShowInStore", "dcmShowInStore",
							"Inventory", "dcmInventory", "OrderLimit", "dcmOrderLimit")
					.withConditionallyUpdateTrFields(data, locale, "Title", "dcmTitle",
							"Description", "dcmDescription", "Instructions", "dcmInstructions")
					.withConditionallySetList(data, "Delivery", "dcmDelivery")
					.withConditionallySetList(data, "Categories", "dcmCategories")
					.withConditionallySetList(data, "Tags", "dcmTag");

			TableUtil.updateRecord(db, req);
		}

		callback.returnEmpty();
	}
}
