package dcraft.cms.db.forms;

import dcraft.cms.thread.db.ThreadUtil;
import dcraft.db.Constants;
import dcraft.db.ICallContext;
import dcraft.db.proc.IStoredProc;
import dcraft.db.tables.TablesAdapter;
import dcraft.filestore.CommonPath;
import dcraft.filestore.mem.MemoryStoreFile;
import dcraft.filevault.VaultUtil;
import dcraft.hub.app.ApplicationHub;
import dcraft.hub.op.*;
import dcraft.interchange.google.RecaptchaUtil;
import dcraft.interchange.slack.SlackUtil;
import dcraft.log.Logger;
import dcraft.schema.SchemaHub;
import dcraft.struct.BaseStruct;
import dcraft.struct.RecordStruct;
import dcraft.struct.Struct;
import dcraft.tenant.Site;
import dcraft.util.StringUtil;
import dcraft.util.TimeUtil;
import dcraft.xml.XElement;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;

public class Submit implements IStoredProc {
	@Override
	public void execute(ICallContext request, OperationOutcomeStruct callback) throws OperatingContextException {
		RecordStruct data = request.getDataAsRecord();

		String captcha = data.getFieldAsString("Captcha");

		RecaptchaUtil.siteVerify(captcha, null, false, new OperationOutcomeRecord() {
			@Override
			public void callback(RecordStruct result) throws OperatingContextException {
				if (this.hasErrors()) {
					Logger.error("Unable to save form - access not verified");
					
					callback.returnEmpty();
				}
				else {
					//System.out.println("Success");
					
					Submit.this.saveWork(request, callback, data);
				}
			}
		});
	}

	public void saveWork(ICallContext request, OperationOutcomeStruct callback, RecordStruct data) throws OperatingContextException {
		String form = data.getFieldAsString("Form");
		
		TablesAdapter db = TablesAdapter.of(request);

		XElement mform = ApplicationHub.getCatalogSettings("CMS-ManagedForm-" + form);
		
		String messagepool = (mform != null) ? mform.getAttribute("MessagePool", "/ManagedForm") : "/ManagedForm";
		
		if (mform == null) {
			String fid = Struct.objectToString(db.firstInIndex("dcmBasicCustomForm", "dcmAlias", form, true));
			
			if (StringUtil.isEmpty(fid)) {
				Logger.error("Managed form not enabled.");
				callback.returnEmpty();
				return;
			}
			
			// TODO consider validating
			
			// TODO message pool
		}
		else if (mform.hasNotEmptyAttribute("Type")) {
			if (! SchemaHub.validateType(true, false, data.getField("Data"), mform.getAttribute("Type"))) {
				callback.returnEmpty();
				return;
			}
		}
		
		Site site = OperationContext.getOrThrow().getSite();
		String event = site.getTenant().getAlias() + "-" + site.getAlias() + " - form submission started: " + form;
		SlackUtil.serverEvent(null, event, null);


		// ======== create thread =========
		
		// don't deliver this yet, have user confirm first
		ZonedDateTime future = LocalDate.of(3000, 1, 1).atStartOfDay(ZoneId.of("UTC"));
		
		String id = ThreadUtil.createThread(db,
				"Website " + form + " form: " + data.getFieldAsString("Title"),
				false,"ManagedForm", Constants.DB_GLOBAL_ROOT_RECORD, future, null);
		
		ThreadUtil.addContent(db, id, "_Form submitted_", "SafeMD");
		
		ThreadUtil.addParty(db, id, messagepool, "/InBox", null);
		
		db.setScalar("dcmThread", id, "dcmManagedFormName", form);
		//db.setStaticScalar("dcmThread", id, "dcmManagedFormToken", token.getFieldAsString("Token"));
		
		//data.with("Token", token.getFieldAsString("Token"));
		data.with("Thread", id);
		data.with("SubmitAt", TimeUtil.now());
		//token.with("Uuid", db.getStaticScalar("dcmThread", id, "dcmUuid"));
		
		String uuid = Struct.objectToString(db.getScalar("dcmThread", id, "dcmUuid"));
		
		// ======== save the file =========
		CommonPath path = CommonPath.from("/" + id + "/data.json");
		
		MemoryStoreFile msource = MemoryStoreFile.of(path)
				.with(data.toPrettyString());

		VaultUtil.prepTxTransfer(id);
		
		VaultUtil.transferAfterToken("ManagedForms", msource, path, id, new OperationOutcomeStruct() {
			@Override
			public void callback(BaseStruct result) throws OperatingContextException {
				callback.returnValue(RecordStruct.record()
						.with("Token", id)
						.with("Uuid", uuid)
				);
			}
		});
	}
}
