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

import dcraft.db.request.query.CollectorField;
import dcraft.db.request.query.LoadRecordRequest;
import dcraft.db.request.query.SelectDirectRequest;
import dcraft.db.request.query.SelectFields;
import dcraft.db.request.query.WhereEqual;
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
import dcraft.struct.RecordStruct;
import dcraft.struct.Struct;
import dcraft.tenant.Site;

public class Products {
	/******************************************************************
	 * Categories
	 ******************************************************************/	
	static public boolean handleCategories(ServiceRequest request, OperationOutcomeStruct callback) throws OperatingContextException {
		String op = request.getOp();

		RecordStruct rec = request.getDataAsRecord();

		if ("Load".equals(op)) {
			LoadRecordRequest req = LoadRecordRequest.of("dcmCategory")
				.withId(rec.getFieldAsString("Id"))
				.withNow()
				.withSelect(SelectFields.select()
					.with("Id")
					.with("dcmTitle", "Title")
					.with("dcmAlias", "Alias")
					.with("dcmMode", "Mode")
					.with("dcmParent", "Parent")
					.with("dcmDescription", "Description")
					.with("dcmCustomDisplayField", "CustomDisplayField", null, true)
					.with("dcmShipAmount", "ShipAmount")
				);  
			
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
			ServiceHub.call(UpdateRecordRequest.update()
					.withTable("dcmCategory")
					.withId(rec.getFieldAsString("Id"))
					.withConditionallySetFields(rec, "Title", "dcmTitle", "Alias", "dcmAlias", "Mode", "dcmMode", "Parent", "dcmParent", "Description", "dcmDescription", "ShipAmount", "dcmShipAmount")
					.toServiceRequest()
					.withOutcome(callback)
			);
			
			return true;
		}
		
		if ("Add".equals(op)) {
			ServiceHub.call(InsertRecordRequest.insert()
					.withTable("dcmCategory")
					.withConditionallySetFields(rec, "Title", "dcmTitle", "Alias", "dcmAlias",
							"Mode", "dcmMode", "Parent", "dcmParent", "Description", "dcmDescription",
							"ShipAmount", "dcmShipAmount"
					)
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

								/* TODO review what to do here...probably use the Bucket instead
								fs..addFolder(new CommonPath(path), new FuncCallback<IFileStoreFile>() {
									@Override
									public void callback() {
										request.returnValue(result);

										// TODO remove this - it is specific to a website - make general purpose setting instead
										/*
										if (!this.hasErrors()) {
											CommonPath metapath = this.getResult().resolvePath(new CommonPath("/meta.json"));

											fs.getFileDetail(metapath, new FuncCallback<IFileStoreFile>() {
												@Override
												public void callback() {
													if (!this.hasErrors()) {
														RecordStruct meta = new RecordStruct(
																new FieldStruct("Variations", new ListStruct(
																		new RecordStruct(
																				new FieldStruct("ExactWidth", 175),
																				new FieldStruct("ExactHeight", 150),
																				new FieldStruct("Alias", "full"),
																				new FieldStruct("Name", "Full Size")
																		)
																))
														);

														this.getResult().writeAllText(meta.toPrettyString(), new OperationCallback() {
															@Override
															public void callback() {
																request.returnValue(result);
															}
														});
													}
													else {
														request.returnValue(result);
													}
												}
											});
										}
										else {
											request.returnValue(result);
										}
										* /
									}
								});
								*/
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
	 * Coupons
	 ******************************************************************/	
	
	static public boolean handleCoupons(ServiceRequest request, OperationOutcomeStruct callback) throws OperatingContextException {
		String op = request.getOp();

		RecordStruct rec = request.getDataAsRecord();
		
		if ("Load".equals(op)) {
			ServiceHub.call(new LoadRecordRequest()
					.withTable("dcmDiscount")
					.withId(rec.getFieldAsString("Id"))			// TODO load by coupon Code too
					.withNow()
					.withSelect(SelectFields.select()
							.with("Id")
							.with("dcmTitle", "Title")
							.with("dcmCode", "Code")
							.with("dcmType", "Type")
							.with("dcmMode", "Mode")
							.with("dcmAmount", "Amount")
							.with("dcmMinimumOrder", "MinimumOrder")
							.with("dcmStart", "Start")
							.with("dcmExpire", "Expire")
							.with("dcmAutomatic", "Automatic")
							.with("dcmOneTimeUse", "OneTimeUse")
							.with("dcmWasUsed", "WasUsed")
					)
					.toServiceRequest()
					.withOutcome(callback)
			);
			
			return true;
		}
		
		if ("Update".equals(op)) {

			ServiceHub.call(UpdateRecordRequest.update()
					.withTable("dcmDiscount")
					.withId(rec.getFieldAsString("Id"))
					.withConditionallySetFields(rec, "Title", "dcmTitle", "Type", "dcmType", "Mode", "dcmMode",
							"Code", "dcmCode", "MinimumOrder", "dcmMinimumOrder", "Amount", "dcmAmount",
							"Start", "dcmStart", "Expire", "dcmExpire", "Automatic", "dcmAutomatic",
							"OneTimeUse", "dcmOneTimeUse", "WasUsed", "dcmWasUsed"
					)
					.toServiceRequest()
					.withOutcome(callback)
			);
			
			return true;
		}
		
		if ("Add".equals(op)) {
			ServiceHub.call(InsertRecordRequest.insert()
					.withTable("dcmDiscount")
					.withConditionallySetFields(rec, "Title", "dcmTitle", "Type", "dcmType", "Mode", "dcmMode",
							"Code", "dcmCode", "MinimumOrder", "dcmMinimumOrder", "Amount", "dcmAmount",
							"Start", "dcmStart", "Expire", "dcmExpire", "Automatic", "dcmAutomatic",
							"OneTimeUse", "dcmOneTimeUse", "WasUsed", "dcmWasUsed"
					)
					.toServiceRequest()
					.withOutcome(new OperationOutcomeStruct() {
						@Override
						public void callback(Struct result) throws OperatingContextException {
							if (! this.hasErrors()) {
								/*
								Site site = OperationContext.getOrThrow().getSite();

								Path catpath = site.resolvePath("galleries/store/category/" + rec.getFieldAsString("Alias"));

								try {
									Files.createDirectories(catpath);
								}
								catch (IOException x) {
									// TODO Auto-generated catch block
									x.printStackTrace();
								}
								*/

								/* TODO review, use Bucket instead
								fs.addFolder(new CommonPath(path), new FuncCallback<IFileStoreFile>() {
									@Override
									public void callback() {
										request.returnValue(result);
									}
								});
								*/
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
			ServiceHub.call(RetireRecordRequest.of("dcmDiscount", rec.getFieldAsString("Id"))
					.toServiceRequest()
					.withOutcome(callback)
			);

			return true;
		}
		
		if ("Revive".equals(op)) {
			ServiceHub.call(ReviveRecordRequest.of("dcmDiscount", rec.getFieldAsString("Id"))
					.toServiceRequest()
					.withOutcome(callback)
			);

			return true;
		}
		
		if ("List".equals(op)) {
			ServiceHub.call(
					SelectDirectRequest.of("dcmDiscount")
						.withSelect(SelectFields.select()
							.with("Id")
							.with("dcmTitle", "Title")
							.with("dcmCode", "Code")
							.with("dcmType", "Type")
							.with("dcmMode", "Mode")
							.with("dcmAmount", "Amount")
							.with("dcmMinimumOrder", "MinimumOrder")
							.with("dcmStart", "Start")
							.with("dcmExpire", "Expire")
							.with("dcmAutomatic", "Automatic")
							.with("dcmOneTimeUse", "OneTimeUse")
							.with("dcmWasUsed", "WasUsed")
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
		
		if ("Load".equals(op)) {
			ServiceHub.call(LoadRecordRequest.of("dcmProduct")
					.withId(rec.getFieldAsString("Id"))
					.withNow()
					.withSelect(new SelectFields()
							.with("Id")
							.with("dcmTitle", "Title")
							.with("dcmAlias", "Alias")
							.with("dcmSku", "Sku")
							.with("dcmDescription", "Description")
							.with("dcmInstructions", "Instructions")
							.with("dcmImage", "Image")
							.with("dcmCustomDisplayField", "CustomDisplayField", null, true)
							.with("dcmCategories", "Categories")
							.with("dcmCategoryPosition", "CategoryPosition")
							.with("dcmPrice", "Price")
							.with("dcmVariablePrice", "VariablePrice")
							.with("dcmMinimumPrice", "MinimumPrice")
							.with("dcmShipAmount", "ShipAmount")
							.with("dcmShipWeight", "ShipWeight")
							.with("dcmShipCost", "ShipCost")
							.with("dcmTaxFree", "TaxFree")
							.with("dcmShowInStore", "ShowInStore")
							.with("dcmDelivery", "Delivery")
							.with("dcmTag", "Tags")
					)
				.toServiceRequest()
				.withOutcome(callback)
			);
			
			return true;
		}
		
		if ("Update".equals(op)) {
			ServiceHub.call(UpdateRecordRequest.update()
					.withTable("dcmProduct")
					.withId(rec.getFieldAsString("Id"))
					.withConditionallySetFields(rec, "Title", "dcmTitle", "Alias", "dcmAlias", "Sku", "dcmSku",
							"Description", "dcmDescription", "Instructions", "dcmInstructions", "Image", "dcmImage",
							"Price", "dcmPrice", "ShipAmount", "dcmShipAmount", "ShipWeight", "dcmShipWeight",
							"VariablePrice", "dcmVariablePrice", "MinimumPrice", "dcmMinimumPrice",
							"ShipCost", "dcmShipCost", "TaxFree", "dcmTaxFree", "ShowInStore", "dcmShowInStore")
					.withConditionallySetList(rec, "Delivery", "dcmDelivery")
					.withConditionallySetList(rec, "Categories", "dcmCategories")
					.withConditionallySetList(rec, "Tags", "dcmTag")
				.toServiceRequest()
				.withOutcome(callback)
			);
			
			return true;
		}
		
		if ("Add".equals(op)) {
			ServiceHub.call(InsertRecordRequest.insert()
					.withTable("dcmProduct")
					.withConditionallySetFields(rec, "Title", "dcmTitle", "Alias", "dcmAlias", "Sku", "dcmSku",
							"Description", "dcmDescription", "Instructions", "dcmInstructions", "Image", "dcmImage",
							"Price", "dcmPrice", "ShipAmount", "dcmShipAmount", "ShipWeight", "dcmShipWeight",
							"VariablePrice", "dcmVariablePrice", "MinimumPrice", "dcmMinimumPrice",
							"ShipCost", "dcmShipCost", "TaxFree", "dcmTaxFree", "ShowInStore", "dcmShowInStore")
					.withConditionallySetList(rec, "Delivery", "dcmDelivery")
					.withConditionallySetList(rec, "Categories", "dcmCategories")
					.withConditionallySetList(rec, "Tags", "dcmTag")
				.toServiceRequest()
				.withOutcome(new OperationOutcomeStruct() {
					@Override
					public void callback(Struct result) throws OperatingContextException {
						if (! this.hasErrors()) {
							Site site = OperationContext.getOrThrow().getSite();

							FileStore gfs = site.getVault("Galleries").getFileStore();

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
						
						if (rec.getFieldAsBooleanOrFalse("ShowInStore")) {
							rec.removeField("ShowInStore");
							callback.returnValue(rec);
							return;
						}
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
					.with("dcmShowInStore", "ShowInStore");
			
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

		return false;
	}
}
