package dcraft.cms.store.db.products;

import dcraft.db.ICallContext;
import dcraft.db.proc.IStoredProc;
import dcraft.db.request.query.LoadRecordRequest;
import dcraft.db.request.query.SelectFields;
import dcraft.db.request.update.DbRecordRequest;
import dcraft.db.request.update.InsertRecordRequest;
import dcraft.db.tables.TableUtil;
import dcraft.db.tables.TablesAdapter;
import dcraft.db.util.DbUtil;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
import dcraft.hub.op.OperationOutcomeStruct;
import dcraft.log.Logger;
import dcraft.schema.DbTable;
import dcraft.service.ServiceHub;
import dcraft.struct.ListStruct;
import dcraft.struct.RecordStruct;
import dcraft.struct.Struct;
import dcraft.util.StringUtil;

public class Load implements IStoredProc {
	@Override
	public void execute(ICallContext request, OperationOutcomeStruct callback) throws OperatingContextException {
		RecordStruct data = request.getDataAsRecord();

		TablesAdapter db = TablesAdapter.ofNow(request);
		
		SelectFields flds = SelectFields.select()
				.with("Id")
				.with("dcmAlias", "Alias")
				.with("dcmSku", "Sku")
				.with("dcmImage", "Image")
				.with("dcmCustomDisplayField", "CustomDisplayField", null, true)
				.with("dcmCategories", "Categories")
				//.with("dcmCategoryPosition", "CategoryPosition")
				.with("dcmPrice", "Price")
				.with("dcmSalePrice", "SalePrice")
				.with("dcmVariablePrice", "VariablePrice")
				.with("dcmMinimumPrice", "MinimumPrice")
				.with("dcmShipAmount", "ShipAmount")
				.with("dcmShipWeight", "ShipWeight")
				.with("dcmShipCost", "ShipCost")
				.with("dcmInventory", "Inventory")
				.with("dcmOrderLimit", "OrderLimit")
				.with("dcmTaxFree", "TaxFree")
				.with("dcmShowInStore", "ShowInStore")
				.with("dcmDelivery", "Delivery")
				.with("dcmTag", "Tags");
		
		String tr = data.getFieldAsString("TrLocale");
		
		if (StringUtil.isEmpty(tr) || tr.equals(OperationContext.getOrThrow().getTenant().getResources().getLocale().getDefaultLocale())) {
			flds
					// .withComposer("dcTranslate", "Title", "dcmTitle", null)
					.with("dcmTitle", "Title")
					.with("dcmDescription", "Description")
					.with("dcmInstructions", "Instructions");
		}
		else {
			flds
					.withSubField("dcmTitleTr", tr, "Title")
					.withSubField("dcmDescriptionTr", tr, "Description")
					.withSubField("dcmInstructionsTr", tr, "Instructions");
		}
		
		String prodid = data.getFieldAsString("Id");
		
		if (StringUtil.isEmpty(prodid)) {
			prodid = Struct.objectToString(db.firstInIndex("dcmProduct", "dcmAlias", data.getFieldAsString("Alias"), true));
		}
		
		if (! db.isCurrent("dcmProduct", prodid)) {
			Logger.error("Unable to find product");
			callback.returnEmpty();
			return;
		}

		callback.returnValue(
				TableUtil.getRecord(db, OperationContext.getOrThrow(), "dcmProduct", prodid, flds)
		);
	}
}
