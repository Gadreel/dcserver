package dcraft.cms.store;

import dcraft.db.request.query.CollectorField;
import dcraft.db.request.query.LoadRecordRequest;
import dcraft.db.request.query.SelectDirectRequest;
import dcraft.db.request.query.SelectFields;
import dcraft.db.request.query.WhereOr;
import dcraft.db.request.query.WhereUtil;
import dcraft.db.request.update.DbRecordRequest;
import dcraft.db.request.update.InsertRecordRequest;
import dcraft.db.request.update.RetireRecordRequest;
import dcraft.db.request.update.ReviveRecordRequest;
import dcraft.db.request.update.UpdateRecordRequest;
import dcraft.hub.app.ApplicationHub;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
import dcraft.hub.op.OperationOutcomeStruct;
import dcraft.log.Logger;
import dcraft.service.ServiceHub;
import dcraft.service.ServiceRequest;
import dcraft.struct.RecordStruct;
import dcraft.struct.Struct;
import dcraft.tenant.Site;
import dcraft.util.StringUtil;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class GiftRegistry {
	/******************************************************************
	 * GiftRegistry
	 ******************************************************************/
	static public boolean handle(ServiceRequest request, OperationOutcomeStruct callback) throws OperatingContextException {
		String op = request.getOp();
		
		RecordStruct rec = request.getDataAsRecord();
		
		if ("Load".equals(op)) {
			LoadRecordRequest req = LoadRecordRequest.of("dcmGiftRegistry")
					.withId(rec.getFieldAsString("Id"))
					.withSelect(SelectFields.select()
							.with("Id")
							.with("dcmFor", "For")
							.with("dcmIntro", "Intro")
							.with("dcmDetail", "Detail")
							.with("dcmEventType", "EventType")
							.with("dcmEventDate", "EventDate")
							.withSubquery("dcmProducts", "Products", SelectFields.select()
									.with("Id", "Product")
									.with("dcmTitle", "Title")
									.with("dcmAlias", "Alias")
									.with("dcmImage", "Image")
							)
					);
			
			ServiceHub.call(req.toServiceRequest().withOutcome(callback));
			
			return true;
		}
		
		if ("Update".equals(op)) {
			DbRecordRequest req = UpdateRecordRequest.update()
					.withTable("dcmGiftRegistry")
					.withId(rec.getFieldAsString("Id"))
					.withConditionallySetFields(rec, "For", "dcmFor", "Intro", "dcmIntro", "Detail", "dcmDetail",
							"EventType", "dcmEventType", "EventDate", "dcmEventDate");
			
			// may need to adjust this idea
			if (rec.hasField("For"))
				req.withUpdateField("dcmKeywords", rec.isNotFieldEmpty("For") ? rec.getFieldAsString("For").toLowerCase() : null);
			
			ServiceHub.call(req
					.toServiceRequest()
					.withOutcome(callback)
			);
			
			return true;
		}

		if ("Add".equals(op)) {
			DbRecordRequest req = InsertRecordRequest.insert()
					.withTable("dcmGiftRegistry")
					.withConditionallySetFields(rec, "For", "dcmFor", "Intro", "dcmIntro", "Detail", "dcmDetail",
							"EventType", "dcmEventType", "EventDate", "dcmEventDate");
			
			// may need to adjust this idea
			if (rec.hasField("For"))
				req.withUpdateField("dcmKeywords", rec.isNotFieldEmpty("For") ? rec.getFieldAsString("For").toLowerCase() : null);
			
			ServiceHub.call(req
					.toServiceRequest()
					.withOutcome(callback)
			);
			
			return true;
		}

		if ("LinkProduct".equals(op)) {
			DbRecordRequest req = UpdateRecordRequest.update()
					.withTable("dcmGiftRegistry")
					.withId(rec.getFieldAsString("RegistryId"))
					.withSetField("dcmProducts", rec.getFieldAsString("ProductId"), rec.getFieldAsString("ProductId"));

			ServiceHub.call(req
					.toServiceRequest()
					.withOutcome(callback)
			);

			return true;
		}

		if ("UnlinkProduct".equals(op)) {
			DbRecordRequest req = UpdateRecordRequest.update()
					.withTable("dcmGiftRegistry")
					.withId(rec.getFieldAsString("RegistryId"))
					.withRetireField("dcmProducts", rec.getFieldAsString("ProductId"));

			ServiceHub.call(req
					.toServiceRequest()
					.withOutcome(callback)
			);

			return true;
		}

		if ("Retire".equals(op)) {
			ServiceHub.call(RetireRecordRequest.of("dcmGiftRegistry", rec.getFieldAsString("Id"))
					.toServiceRequest()
					.withOutcome(callback)
			);
			
			return true;
		}
		
		if ("Revive".equals(op)) {
			ServiceHub.call(ReviveRecordRequest.of("dcmGiftRegistry", rec.getFieldAsString("Id"))
					.toServiceRequest()
					.withOutcome(callback)
			);
			
			return true;
		}
		
		if ("Search".equals(op)) {
			String term = rec.getFieldAsString("Term");
			
			SelectDirectRequest req = SelectDirectRequest.of("dcmGiftRegistry")
					.withSelect(new SelectFields()
							.with("Id")
							.with("dcmFor", "For")
							.with("dcmEventType", "EventType")
							.with("dcmEventDate", "EventDate")
					);
			
			if (StringUtil.isDataInteger(term) && (term.length() < 7)) {
				req.withCollector(CollectorField.collect()
						.withField("Id")
						// this assumes a single node server, will fail if order was placed on another node
						.withValues(ApplicationHub.getNodeId() + "_" + StringUtil.leftPad(term, 15, '0'))
				);
			}
			else if (StringUtil.isNotEmpty(term)) {
				String[] terms = term.split(" ");
				
				WhereOr where = new WhereOr();
				
				for (String t : terms) {
					WhereUtil.tryWhereContains(where, "dcmKeywords", t.trim().toLowerCase());
				}
				
				if (where.getExpressionCount() > 0)
					req.withWhere(where);
			}
			
			ServiceHub.call(req.toServiceRequest().withOutcome(callback));
			return true;
		}
		
		if ("List".equals(op)) {
			ServiceHub.call(
					SelectDirectRequest.of("dcmGiftRegistry")
							.withSelect(SelectFields.select()
									.with("Id")
									.with("dcmFor", "For")
									.with("dcmEventType", "EventType")
									.with("dcmEventDate", "EventDate")
							)
							.toServiceRequest()
							.withOutcome(callback)
			);
			
			return true;
		}
		
		return false;
	}
}
