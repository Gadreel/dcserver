package dcraft.cms.thread.db.email;

import dcraft.cms.reports.db.EmailActivityUtil;
import dcraft.db.ICallContext;
import dcraft.db.proc.IStoredProc;
import dcraft.db.tables.TablesAdapter;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationOutcomeStruct;
import dcraft.log.Logger;
import dcraft.script.StackUtil;
import dcraft.struct.ListStruct;
import dcraft.struct.RecordStruct;
import dcraft.struct.Struct;
import dcraft.util.MailUtil;
import dcraft.util.StringUtil;
import dcraft.util.TimeUtil;

import javax.mail.internet.AddressException;
import javax.mail.internet.InternetAddress;
import java.time.ZonedDateTime;
import java.util.Locale;

public class EmailActivityForThreadCustomRecorder implements IStoredProc {
	@Override
	public void execute(ICallContext request, OperationOutcomeStruct callback) throws OperatingContextException {
		RecordStruct data = request.getDataAsRecord();
		
		TablesAdapter db = TablesAdapter.of(request);

		String tid = data.getFieldAsString("Id");
		String msgid = data.getFieldAsString("MessageId");
		ListStruct actualAddresses = data.getFieldAsList("ActualAddresses");

		if (! db.isCurrent("dcmThread", tid)) {
			Logger.error("Thread is missing: " + tid);
			callback.returnEmpty();
			return;
		}

		// if no addresses then no activity to recoord
		if (actualAddresses.size() == 0) {
			callback.returnEmpty();
			return;
		}

		String actid = EmailActivityUtil.createGetRecord(db, msgid);

		db.updateScalar("dcmOutboundEmailActivity", actid, "dcmReportHandler", "dcmThreadEmailActivityCustomReporter");
		db.updateScalar("dcmOutboundEmailActivity", actid, "dcmHandlerData", RecordStruct.record().with("ThreadId", tid));

		for (int i = 0; i < actualAddresses.getSize(); i++) {
			String address = MailUtil.cleanEmailDomainName(actualAddresses.getItemAsString(i));

			// TODO should we IDNA domain addresses ? here or in suppression list?
			// need to review how AWS handles and reports unicode addresses

			if (StringUtil.isNotEmpty(address)) {
				db.updateList("dcmThread", tid, "dcmEmailAddress", address, address);
				db.updateList("dcmThread", tid, "dcmEmailMessageId", address, msgid);
				db.updateList("dcmThread", tid, "dcmEmailActivityId", address, actid);
				db.updateList("dcmThread", tid, "dcmEmailState", address, "Sent");
			}
		}

		// TODO there is a rare chance that the email report could have arrived before we made this record (actid)
		// if it is an existing record/report we should now run the trigger
		//  EmailActivityUtil.triggerReportHandler(db, actid, auditkey, data);

		callback.returnValue(RecordStruct.record()
				.with("Id", actid)
		);
	}
}
