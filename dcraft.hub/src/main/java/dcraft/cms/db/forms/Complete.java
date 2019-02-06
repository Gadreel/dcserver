package dcraft.cms.db.forms;

import dcraft.cms.thread.db.ThreadUtil;
import dcraft.db.Constants;
import dcraft.db.ICallContext;
import dcraft.db.proc.IStoredProc;
import dcraft.db.tables.TablesAdapter;
import dcraft.filestore.CommonPath;
import dcraft.filestore.FileStoreFile;
import dcraft.filevault.Vault;
import dcraft.hub.app.ApplicationHub;
import dcraft.hub.op.*;
import dcraft.interchange.google.RecaptchaUtil;
import dcraft.interchange.slack.SlackUtil;
import dcraft.log.Logger;
import dcraft.schema.SchemaHub;
import dcraft.struct.RecordStruct;
import dcraft.struct.Struct;
import dcraft.task.Task;
import dcraft.task.TaskHub;
import dcraft.tenant.Site;
import dcraft.util.TimeUtil;
import dcraft.xml.XElement;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;

public class Complete implements IStoredProc {
	@Override
	public void execute(ICallContext request, OperationOutcomeStruct callback) throws OperatingContextException {
		RecordStruct data = request.getDataAsRecord();

		String form = data.getFieldAsString("Form");

		XElement mform = ApplicationHub.getCatalogSettings("CMS-ManagedForm-" + form);

		if (mform == null) {
			Logger.error("Managed form not enabled.");
			callback.returnEmpty();
			return;
		}

		TablesAdapter db = TablesAdapter.ofNow(request);

		String id = ThreadUtil.getThreadId(db, data);

		Object sfrm = db.getStaticScalar("dcmThread", id, "dcmManagedFormName");

		if (! form.equals(sfrm)) {
			Logger.error("Incorrect form name.");
			callback.returnEmpty();
			return;
		}

		ZonedDateTime now = TimeUtil.now();
		ZonedDateTime delivered = Struct.objectToDateTime(db.getStaticScalar("dcmThread", id, "dcmModified"));

		if (delivered.isBefore(now)) {
			Logger.error("Form already completed.");
			callback.returnEmpty();
			return;
		}

		ThreadUtil.deliver(db, id, now);

		callback.returnEmpty();
	}
}
