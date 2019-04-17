/* ************************************************************************
#
#  designCraft.io
#
#  http://designcraft.io/
#
#  Copyright:
#    Copyright 2014 eTimeline, LLC. All rights reserved.
#
#  License:
#    See the license.txt file in the project's top-level directory for details.
#
#  Authors:
#    * Andy White
#
************************************************************************ */
package dcraft.cms.store;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import dcraft.db.request.query.*;
import dcraft.db.request.update.InsertRecordRequest;
import dcraft.db.request.update.RetireRecordRequest;
import dcraft.db.request.update.ReviveRecordRequest;
import dcraft.db.request.update.UpdateRecordRequest;
import dcraft.filestore.CommonPath;
import dcraft.filestore.FileStore;
import dcraft.filestore.FileStoreFile;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
import dcraft.hub.op.OperationOutcome;
import dcraft.hub.op.OperationOutcomeStruct;
import dcraft.log.Logger;
import dcraft.service.ServiceHub;
import dcraft.service.ServiceRequest;
import dcraft.struct.CompositeStruct;
import dcraft.struct.ListStruct;
import dcraft.struct.RecordStruct;
import dcraft.struct.Struct;
import dcraft.tenant.Site;
import dcraft.util.StringUtil;

public class Products {
	/******************************************************************
	 * Categories
	 ******************************************************************/	
	static public boolean handleCategories(ServiceRequest request, OperationOutcomeStruct callback) throws OperatingContextException {
		String op = request.getOp();

		RecordStruct rec = request.getDataAsRecord();

		if ("Load".equals(op)) {
			SelectFields flds = SelectFields.select()
					.with("Id")
					.with("dcmAlias", "Alias")
					.with("dcmMode", "Mode")
					.with("dcmParent", "Parent")
					.with("dcmShipAmount", "ShipAmount")
					.with("dcmImage", "Image")
					.with("dcmShowInStore", "ShowInStore")
					.with("dcmCustomDisplayField", "CustomDisplayField", null, true);
			String tr = rec.getFieldAsString("TrLocale");
			
			if (StringUtil.isEmpty(tr) || tr.equals(OperationContext.getOrThrow().getTenant().getResources().getLocale().getDefaultLocale())) {
				flds
						// .withComposer("dcTranslate", "Title", "dcmTitle", null)
						.with("dcmTitle", "Title")
						.with("dcmDescription", "Description");
			}
			else {
				flds
						.withSubField("dcmTitleTr", tr, "Title")
						.withSubField("dcmDescriptionTr", tr, "Description");
			}
			
			LoadRecordRequest req = LoadRecordRequest.of("dcmCategory")
				.withId(rec.getFieldAsString("Id"))
				.withNow()
				.withSelect(flds);
			
			ServiceHub.call(req.toServiceRequest()
				.withOutcome(new OperationOutcomeStruct() {
					@Override
					public void callback(Struct result) throws OperatingContextException {
						if (result == null) {
							Logger.error("Unable to load category record");
							callback.returnEmpty();
							return;
						}

						ServiceHub.call(
								SelectDirectRequest.of("dcmCategory")
									.withSelect(SelectFields.select()
										.with("Id")
										.with("dcmTitle", "Title")
										.with("dcmAlias", "Alias")
										.with("dcmMode", "Mode")
										.with("dcmParent", "Parent")
										.with("dcmDescription", "Description"))
									.withCollector(
											CollectorField.collect()
													.withField("dcmParent")
													.withValues(rec.getFieldAsString("Id"))
									)
								.toServiceRequest()
								.withOutcome(new OperationOutcomeStruct() {
									@Override
									public void callback(Struct result2) throws OperatingContextException {
										if (result2 == null) {
											Logger.error("Unable to load category record");
											callback.returnEmpty();
											return;
										}

										((RecordStruct)result).with("Children", result2);

										callback.returnValue(result);
									}
								})
						);
					}
				})
			);
			
			return true;
		}
		
		if ("Update".equals(op)) {
			String locale = rec.getFieldAsString("TrLocale");
			
			ServiceHub.call(UpdateRecordRequest.update()
					.withTable("dcmCategory")
					.withId(rec.getFieldAsString("Id"))
					.withConditionallySetFields(rec, "Alias", "dcmAlias", "Mode", "dcmMode",
							"Parent", "dcmParent", "ShipAmount", "dcmShipAmount",
							"Image", "dcmImage", "ShowInStore", "dcmShowInStore"
					)
					.withConditionallyUpdateTrFields(rec, locale, "Title", "dcmTitle",
							"Description", "dcmDescription")
					.toServiceRequest()
					.withOutcome(callback)
			);
			
			return true;
		}
		
		if ("Add".equals(op)) {
			String locale = rec.getFieldAsString("TrLocale");
			
			ServiceHub.call(InsertRecordRequest.insert()
					.withTable("dcmCategory")
					.withConditionallySetFields(rec, "Alias", "dcmAlias", "Mode", "dcmMode", "Parent", "dcmParent",
							"ShipAmount", "dcmShipAmount", "Image", "dcmImage", "ShowInStore", "dcmShowInStore"
					)
					.withConditionallyUpdateTrFields(rec, locale, "Title", "dcmTitle",
							"Description", "dcmDescription")
					.toServiceRequest()
					.withOutcome(new OperationOutcomeStruct() {
						@Override
						public void callback(Struct result) throws OperatingContextException {
							if (! this.hasErrors()) {
								Site site = OperationContext.getOrThrow().getSite();

								// TODO review - use Bucket instead
								Path catpath = site.resolvePath("galleries/store/category/" + rec.getFieldAsString("Alias"));

								try {
									Files.createDirectories(catpath);
								}
								catch (IOException x) {
									// TODO Auto-generated catch block
									x.printStackTrace();
								}

								callback.returnValue(result);
							}
							else {
								callback.returnValue(result);
							}
						}
					})
			);
			
			return true;
		}
		
		if ("Retire".equals(op)) {
			ServiceHub.call(RetireRecordRequest.of("dcmCategory", rec.getFieldAsString("Id"))
					.toServiceRequest()
					.withOutcome(callback)
			);

			return true;
		}
		
		if ("Revive".equals(op)) {
			ServiceHub.call(ReviveRecordRequest.of("dcmCategory", rec.getFieldAsString("Id"))
					.toServiceRequest()
					.withOutcome(callback)
			);

			return true;
		}
		
		if ("Lookup".equals(op)) {
			ServiceHub.call(SelectDirectRequest.of("dcmCategory")
						.withSelect(SelectFields.select()
								.with("Id")
								.with("dcmTitle", "Title")
								.with("dcmAlias", "Alias")
								.with("dcmMode", "Mode")
								.with("dcmParent", "Parent")
								.with("dcmDescription", "Description"))
						.withCollector(
								CollectorField.collect()
										.withField("dcmAlias")
										.withValues(rec.getFieldAsString("Alias"))
						)
					.toServiceRequest()
					.withOutcome(callback)
			);

			return true;
		}

		if ("List".equals(op)) {
			ServiceHub.call(
				SelectDirectRequest.of("dcmCategory")
					.withSelect(SelectFields.select()
						.with("Id")
						.with("dcmTitle", "Title")
						.with("dcmAlias", "Alias")
						.with("dcmMode", "Mode")
						.with("dcmParent", "Parent")
						.with("dcmDescription", "Description")
					)
					.toServiceRequest()
					.withOutcome(callback)
			);

			return true;
		}

		return false;
	}
	
	/******************************************************************
	 * Products
	 ******************************************************************/	
	static public boolean handleProducts(ServiceRequest request, OperationOutcomeStruct callback) throws OperatingContextException {
		String op = request.getOp();

		RecordStruct rec = request.getDataAsRecord();
		
		if ("Update".equals(op)) {
			String locale = rec.getFieldAsString("TrLocale");
			
			ServiceHub.call(UpdateRecordRequest.update()
					.withTable("dcmProduct")
					.withId(rec.getFieldAsString("Id"))
					.withConditionallyUpdateFields(rec, "Alias", "dcmAlias", "Sku", "dcmSku", "Image", "dcmImage",
							"Price", "dcmPrice", "ShipAmount", "dcmShipAmount", "ShipWeight", "dcmShipWeight",
							"VariablePrice", "dcmVariablePrice", "MinimumPrice", "dcmMinimumPrice",
							"ShipCost", "dcmShipCost", "TaxFree", "dcmTaxFree", "ShowInStore", "dcmShowInStore")
					.withConditionallyUpdateTrFields(rec, locale, "Title", "dcmTitle",
							"Description", "dcmDescription", "Instructions", "dcmInstructions")
					.withConditionallySetList(rec, "Delivery", "dcmDelivery")
					.withConditionallySetList(rec, "Categories", "dcmCategories")
					.withConditionallySetList(rec, "Tags", "dcmTag")
				.toServiceRequest()
				.withOutcome(callback)
			);
			
			return true;
		}
		
		if ("Add".equals(op)) {
			String locale = rec.getFieldAsString("TrLocale");
			
			ServiceHub.call(InsertRecordRequest.insert()
					.withTable("dcmProduct")
					.withConditionallyUpdateFields(rec, "Alias", "dcmAlias", "Sku", "dcmSku", "Image", "dcmImage",
							"Price", "dcmPrice", "ShipAmount", "dcmShipAmount", "ShipWeight", "dcmShipWeight",
							"VariablePrice", "dcmVariablePrice", "MinimumPrice", "dcmMinimumPrice",
							"ShipCost", "dcmShipCost", "TaxFree", "dcmTaxFree", "ShowInStore", "dcmShowInStore")
					.withConditionallyUpdateTrFields(rec, locale, "Title", "dcmTitle",
							"Description", "dcmDescription", "Instructions", "dcmInstructions")
					.withConditionallySetList(rec, "Delivery", "dcmDelivery")
					.withConditionallySetList(rec, "Categories", "dcmCategories")
					.withConditionallySetList(rec, "Tags", "dcmTag")
				.toServiceRequest()
				.withOutcome(new OperationOutcomeStruct() {
					@Override
					public void callback(Struct result) throws OperatingContextException {
						if (! this.hasErrors()) {
							Site site = OperationContext.getOrThrow().getSite();

							FileStore gfs = site.getGalleriesVault().getFileStore();

							gfs.addFolder(CommonPath.from("/store/product/" + rec.getFieldAsString("Alias")), new OperationOutcome<FileStoreFile>() {
								@Override
								public void callback(FileStoreFile file) throws OperatingContextException {
									callback.returnValue(result);
								}
							});
						}
						else {
							callback.returnEmpty();
						}
					}
				})
			);
			
			return true;
		}
		
		if ("Retire".equals(op)) {
			ServiceHub.call(RetireRecordRequest.of("dcmProduct", rec.getFieldAsString("Id"))
					.toServiceRequest()
					.withOutcome(callback)
			);

			return true;
		}
		
		if ("Revive".equals(op)) {
			ServiceHub.call(ReviveRecordRequest.of("dcmProduct", rec.getFieldAsString("Id"))
					.toServiceRequest()
					.withOutcome(callback)
			);

			return true;
		}
		
		if ("Lookup".equals(op)) {
			OperationOutcomeStruct finaloutcome = new OperationOutcomeStruct() {
				@Override
				public void callback(Struct result) throws OperatingContextException {
					if (this.isNotEmptyResult()) {
						RecordStruct rec = Struct.objectToRecord(result);

						ListStruct fields = rec.getFieldAsList("CustomFields");

						if (fields != null) {
							fields.sortRecords("Position", false);

							for (int i = 0; i < fields.size(); i++) {
								fields.getItemAsRecord(i).removeField("Position");
							}
						}

						RecordStruct custom = RecordStruct.record()
								.with("Controls", fields);

						rec.with("CustomFields", custom);

						//System.out.println("str: " + rec.toPrettyString());

						// need lookup even for "hidden" registries
						//if (rec.getFieldAsBooleanOrFalse("ShowInStore")) {
							rec.removeField("ShowInStore");
							callback.returnValue(rec);
							return;
						//}
					}

					callback.returnEmpty();
				}
			};
			
			SelectFields select = new SelectFields()
					.with("Id")
					.with("dcmTitle", "Title")
					.with("dcmAlias", "Alias")
					.with("dcmSku", "Sku")
					.with("dcmDescription", "Description")
					.with("dcmInstructions", "Instructions")
					.with("dcmImage", "Image")
					.with("dcmPrice", "Price")
					.with("dcmVariablePrice", "VariablePrice")
					.with("dcmMinimumPrice", "MinimumPrice")
					.with("dcmTaxFree", "TaxFree")
					.with("dcmDelivery", "Delivery")
					.with("dcmShowInStore", "ShowInStore")
					.withReverseSubquery("CustomFields", "dcmProductCustomFields", "dcmProduct", new SelectFields()
							.with("Id")
							.with("dcmPosition", "Position")
							.with("dcmFieldType", "Type")
							.with("dcmDataType", "DataType")
							.with("dcmLabel", "Label")
							.with("dcmLongLabel", "LongLabel")
							.with("dcmPlaceholder", "Placeholder")
							.with("dcmPattern", "Pattern")
							.with("dcmRequired", "Required")
							.with("dcmMaxLength", "MaxLength")
							.with("dcmHorizontal", "Horizontal")
							.with("dcmPrice", "Price")
							.withGroup("dcmOptionLabel", "Options", "Id", new SelectFields()
									.with("dcmOptionLabel", "Label")
									.with("dcmOptionValue", "Value")
									.with("dcmOptionPrice", "Price")
							)
					);
			
			if (rec.isNotFieldEmpty("Id")) {
				ServiceHub.call(LoadRecordRequest.of("dcmProduct")
						.withId(rec.getFieldAsString("Id"))
						.withSelect(select)
						.toServiceRequest()
						.withOutcome(finaloutcome)
				);
			}
			else if (rec.isNotFieldEmpty("Alias")) {
				ServiceHub.call(
						SelectDirectRequest.of("dcmProduct")
								.withSelect(select)
								.withCollector(
										CollectorField.collect()
												.withField("dcmAlias")
												.withValues(rec.getFieldAsString("Alias"))
								)
								.toServiceRequest()
								.withOutcome(new OperationOutcomeStruct() {
									@Override
									public void callback(Struct result) throws OperatingContextException {
										if (this.isNotEmptyResult()) {
											// return the first item
											finaloutcome.returnValue(Struct.objectToList(result).getItem(0));
										}
										else {
											finaloutcome.returnEmpty();
										}
									}
								})
				);
			}
			else {
				Logger.error("Lookup requires Alias or Id");
				callback.returnEmpty();
			}
			
			return true;
		}
		
		if ("CatList".equals(op)) {
			// TODO support category alias lookup too

			ServiceHub.call(LoadRecordRequest.of("dcmCategory")
				.withId(rec.getFieldAsString("Id"))
				.withNow()
				.withSelect(new SelectFields()
						.with("Id", "CategoryId")
						.with("dcmTitle", "Category")
						.with("dcmAlias", "CategoryAlias")
				)
				.toServiceRequest()
				.withOutcome(new OperationOutcomeStruct() {
					@Override
					public void callback(Struct result) throws OperatingContextException {
						if (result == null) {
							Logger.error("Unable to load category record");
							callback.returnEmpty();
							return;
						}

						ServiceHub.call(
								SelectDirectRequest.of("dcmProduct")
									.withSelect(SelectFields.select()
											.with("Id")
											.with("dcmTitle", "Title")
											.with("dcmAlias", "Alias")
											.with("dcmSku", "Sku")
											.with("dcmShowInStore", "ShowInStore")
											.with("dcmPrice", "Price")
											.with("dcmDescription", "Description")
											.with("dcmImage", "Image")
									)
									.withCollector(
											CollectorField.collect()
												.withField("dcmCategories")
												.withValues(rec.getFieldAsString("Id"))
									)
									.toServiceRequest()
									.withOutcome(new OperationOutcomeStruct() {
										@Override
										public void callback(Struct result2) throws OperatingContextException {
											if (result2 == null) {
												Logger.error("Unable to load product list");
												callback.returnEmpty();
												return;
											}

											((RecordStruct)result).with("Products", result2);

											callback.returnValue(result);
										}
									})
						);
					}
				})
			);

			return true;
		}

		if ("List".equals(op)) {
			ServiceHub.call(
					SelectDirectRequest.of("dcmProduct")
						.withSelect(SelectFields.select()
							.with("Id")
							.with("dcmTitle", "Title")
							.with("dcmAlias", "Alias")
							.with("dcmSku", "Sku")
							.with("dcmShowInStore", "ShowInStore")
							.with("dcmPrice", "Price")
							.with("dcmDescription", "Description")
							.with("dcmImage", "Image")
						)
						.toServiceRequest()
						.withOutcome(callback)
			);
			
			return true;
		}

		if ("SearchAvailable".equals(op)) {
			ServiceHub.call(
					SelectDirectRequest.of("dcmProduct")
							.withSelect(SelectFields.select()
									.with("Id")
									.withComposer("dcTranslate","Title", "dcmTitle", null)
									.with("dcmAlias", "Alias")
									.with("dcmSku", "Sku")
									.with("dcmPrice", "Price")
									.withComposer("dcTranslate","Description", "dcmDescription", null)
									.withComposer("dcmStoreImage", "Image")
									.withComposer("dcTermScore", "Score")
									.with("dcmVariablePrice", "VariablePrice")
									.withSubquery("dcmCategories", "Categories", SelectFields.select()
											.with("Id")
											.withComposer("dcTranslate","Title", "dcmTitle", null)
											.with("dcmAlias", "Alias")
									)
									.with("dcmDelivery", "Delivery")
							)
							.withWhere(WhereAnd.of(
									WhereEqual.of("dcmShowInStore", true),
									WhereTerm.term()
										.withFields(
												WhereField.of("dcmTitle")
														.withValue(RecordStruct.record().with("_RankMultiplier", 3)),
												WhereField.of("dcmDescription")
										)
										.withValueTwo(rec.getFieldAsString("Term"))
							))
							.toServiceRequest()
							.withOutcome(callback)
			);

			return true;
		}

		return false;
	}
}
