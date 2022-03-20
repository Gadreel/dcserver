package dcraft.cms.store.db.products;

import dcraft.cms.store.db.Util;
import dcraft.db.ICallContext;
import dcraft.db.proc.IStoredProc;
import dcraft.db.request.update.DbRecordRequest;
import dcraft.db.request.update.InsertRecordRequest;
import dcraft.db.tables.TableUtil;
import dcraft.db.tables.TablesAdapter;
import dcraft.filestore.CommonPath;
import dcraft.filestore.FileStore;
import dcraft.filestore.FileStoreFile;
import dcraft.hub.op.*;
import dcraft.service.ServiceHub;
import dcraft.struct.RecordStruct;
import dcraft.struct.Struct;
import dcraft.tenant.Site;
import dcraft.util.TimeUtil;

public class Add implements IStoredProc {
	@Override
	public void execute(ICallContext request, OperationOutcomeStruct callback) throws OperatingContextException {
		RecordStruct data = request.getDataAsRecord();

		TablesAdapter db = TablesAdapter.of(request);

		try (OperationMarker om = OperationMarker.create()) {
			String locale = data.getFieldAsString("TrLocale");

			DbRecordRequest req = InsertRecordRequest.insert()
					.withTable("dcmProduct")
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

			String newid = TableUtil.updateRecord(db, req);

			if (! om.hasErrors()) {
				Site site = OperationContext.getOrThrow().getSite();

				FileStore gfs = site.getGalleriesVault().getFileStore();

				gfs.addFolder(CommonPath.from("/store/product/" + data.getFieldAsString("Alias")), new OperationOutcome<FileStoreFile>() {
					@Override
					public void callback(FileStoreFile file) throws OperatingContextException {
					callback.returnValue(
							RecordStruct.record()
									.with("Id", newid)
					);
					}
				});

				return;
			}
		}

		callback.returnEmpty();
	}
}
