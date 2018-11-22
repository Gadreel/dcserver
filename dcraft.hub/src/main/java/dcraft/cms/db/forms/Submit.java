package dcraft.cms.db.forms;

import dcraft.cms.thread.db.ThreadUtil;
import dcraft.db.Constants;
import dcraft.db.ICallContext;
import dcraft.db.proc.IStoredProc;
import dcraft.db.request.query.SelectFields;
import dcraft.db.tables.TableUtil;
import dcraft.db.tables.TablesAdapter;
import dcraft.filestore.CommonPath;
import dcraft.filestore.FileStoreFile;
import dcraft.filestore.mem.MemoryStoreFile;
import dcraft.filevault.Vault;
import dcraft.filevault.VaultUtil;
import dcraft.hub.ResourceHub;
import dcraft.hub.app.ApplicationHub;
import dcraft.hub.op.*;
import dcraft.interchange.google.RecaptchaUtil;
import dcraft.log.Logger;
import dcraft.schema.SchemaHub;
import dcraft.struct.ListStruct;
import dcraft.struct.RecordStruct;
import dcraft.struct.Struct;
import dcraft.task.Task;
import dcraft.task.TaskHub;
import dcraft.util.StringUtil;
import dcraft.util.TimeUtil;
import dcraft.xml.XElement;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

public class Submit implements IStoredProc {
	@Override
	public void execute(ICallContext request, OperationOutcomeStruct callback) throws OperatingContextException {
		RecordStruct data = request.getDataAsRecord();

		String captcha = data.getFieldAsString("Captcha");

		RecaptchaUtil.siteVerify(captcha, null, new OperationOutcomeRecord() {
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

		XElement mform = ApplicationHub.getCatalogSettings("CMS-ManagedForm-" + form);

		if (mform == null) {
			Logger.error("Managed form not enabled.");
			callback.returnEmpty();
			return;
		}

		if (mform.hasNotEmptyAttribute("Type")) {
			if (! SchemaHub.validateType(data.getField("Data"), mform.getAttribute("Type"))) {
				callback.returnEmpty();
				return;
			}
		}

		String messagepool = mform.getAttribute("MessagePool", "/ManagedForm");
		
		TablesAdapter db = TablesAdapter.ofNow(request);
		
		// ======== create thread =========
		
		// don't deliver this yet, have user confirm first
		ZonedDateTime future = LocalDate.of(3000, 1, 1).atStartOfDay(ZoneId.of("UTC"));
		
		String id = ThreadUtil.createThread(db,
				"Website " + form + " form: " + data.getFieldAsString("Title"),
				false,"ManagedForm", Constants.DB_GLOBAL_ROOT_RECORD, future, null);
		
		ThreadUtil.addContent(db, id, "_Form submitted_", "SafeMD");
		
		ThreadUtil.addParty(db, id, messagepool, "/InBox", null);
		
		db.setStaticScalar("dcmThread", id, "dcmManagedFormName", form);
		//db.setStaticScalar("dcmThread", id, "dcmManagedFormToken", token.getFieldAsString("Token"));
		
		//data.with("Token", token.getFieldAsString("Token"));
		data.with("Thread", id);
		data.with("SubmitAt", TimeUtil.now());
		//token.with("Uuid", db.getStaticScalar("dcmThread", id, "dcmUuid"));
		
		String uuid = Struct.objectToString(db.getStaticScalar("dcmThread", id, "dcmUuid"));
		
		// ======== save the file =========
		CommonPath path = CommonPath.from("/" + id + "/data.json");
		
		MemoryStoreFile msource = MemoryStoreFile.of(path)
				.with(data.toPrettyString());
		
		// TODO ideally we would use a form Tx here so that the managed form could add files to the transaction
		//token.with("TxId",
		
		VaultUtil.setSessionToken(id);
		
		VaultUtil.transfer("ManagedForms", msource, path, id, new OperationOutcomeStruct() {
			@Override
			public void callback(Struct result) throws OperatingContextException {
				callback.returnValue(RecordStruct.record()
						.with("Token", id)
						.with("Uuid", uuid)
				);
			}
		});

		/*
		Vault mfbucket = OperationContext.getOrThrow().getSite().getVault("ManagedForms");
		
		RecordStruct vrequest = RecordStruct.record()
				.with("Vault", "ManagedForms")
				.with("Params", RecordStruct.record()
						.with("Token", refid)
				);

		mfbucket.allocateUploadToken(data, false, new OperationOutcomeStruct() {
			@Override
			public void callback(Struct res) throws OperatingContextException {
				if (res == null) {
					callback.returnEmpty();
					return;
				}

				RecordStruct token = Struct.objectToRecord(res);

			}
		});
		*/
	}
}