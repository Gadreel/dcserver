package dcraft.mail;

import dcraft.db.request.update.DbRecordRequest;
import dcraft.db.request.update.InsertRecordRequest;
import dcraft.db.request.update.UpdateRecordRequest;
import dcraft.db.tables.TableUtil;
import dcraft.db.tables.TablesAdapter;
import dcraft.hub.ResourceHub;
import dcraft.hub.op.OperatingContextException;
import dcraft.log.Logger;
import dcraft.schema.DataType;
import dcraft.struct.BaseStruct;
import dcraft.struct.RecordStruct;
import dcraft.struct.Struct;
import dcraft.util.StringUtil;
import dcraft.util.TimeUtil;

public class EmailActivityUtil {
    static public String createGetRecord(TablesAdapter db, String messageid) throws OperatingContextException {
        String actid = Struct.objectToString(db.firstInIndex("dcmOutboundEmailActivity", "dcmMessageId", messageid, false));

        if (StringUtil.isEmpty(actid)) {
            DbRecordRequest req = InsertRecordRequest.insert()
                    .withTable("dcmOutboundEmailActivity")
                    .withUpdateField("dcmMessageId", messageid)
                    .withUpdateField("dcmCreatedAt", TimeUtil.now());

            actid = TableUtil.updateRecord(db, req);
        }

        return actid;
    }

    static public boolean triggerReportHandler(TablesAdapter db, String id, String auditkey, RecordStruct reportData) throws OperatingContextException {
        String reportHandler = Struct.objectToString(db.getScalar("dcmOutboundEmailActivity", id, "dcmReportHandler"));

        if (StringUtil.isNotEmpty(reportHandler)) {
            DataType htype = ResourceHub.getResources().getSchema().getType(reportHandler);

            if (htype != null) {
                BaseStruct handler = htype.create();

                if (handler instanceof IEmailActivityForCustomReporter) {
                    RecordStruct handlerData =  Struct.objectToRecord(db.getScalar("dcmOutboundEmailActivity", id, "dcmHandlerData"));

                    return ((IEmailActivityForCustomReporter) handler).reportReceived(id, auditkey, reportData, handlerData);
                }
                else {
                    Logger.error("Report handler not created or does not implement interface.");
                }
            }
            else {
                Logger.error("Report handler not found.");
            }
        }

        return true;
    }

    static public String suppressEmail(TablesAdapter db, EmailAddress email, String actid) throws OperatingContextException {
        if (email == null) {
            Logger.info("Email address unable to suppress: " + email + " activity: " + actid);
            return null;
        }

        String emailaddress = email.toStringForIndex();

        DbRecordRequest req = null;
        String contactId = Struct.objectToString(db.firstInIndex("dcmContactEmail", "dcmIndexAddress", emailaddress, false));

        if (StringUtil.isEmpty(contactId)) {
            req = InsertRecordRequest.insert()
                    .withTable("dcmContactEmail")
                    .withUpdateField("dcmDisplayAddress", email.toString())
                    .withUpdateField("dcmIndexAddress", emailaddress);

            if (email.hasPersonal())
                    req.withUpdateField("dcmName", email.getPersonal());
        }
        else {
            req = UpdateRecordRequest.update()
                    .withTable("dcmContactEmail")
                    .withId(contactId);
        }

        req
                .withUpdateField("dcmSuppressedAt", TimeUtil.now())
                .withUpdateField("dcmSuppressedReason", "aws feedback: " + actid);

        if (StringUtil.isNotEmpty(actid)) {
            req.withUpdateField("dcmSuppressedActivityId", actid, actid);
        }

        contactId = TableUtil.updateRecord(db, req);

        Logger.info("Email address suppressed: " + emailaddress + " activity: " + actid);

        return contactId;
    }

    static public String unsuppressEmail(TablesAdapter db, EmailAddress email, String actid) throws OperatingContextException {
        if (email == null) {
            Logger.info("Email address unable to unsuppress: " + email + " activity: " + actid);
            return null;
        }

        String emailaddress = email.toStringForIndex();

        String contactId = Struct.objectToString(db.firstInIndex("dcmContactEmail", "dcmIndexAddress", emailaddress, false));

        if (StringUtil.isNotEmpty(contactId)) {
            DbRecordRequest req = UpdateRecordRequest.update()
                    .withTable("dcmContactEmail")
                    .withId(contactId)
                    .withRetireField("dcmSuppressedAt")
                    .withRetireField("dcmSuppressedReason");

            if (StringUtil.isNotEmpty(actid)) {
                req.withRetireField("dcmSuppressedActivityId", actid);
            }

            TableUtil.updateRecord(db, req);

            Logger.info("Email address unsuppressed: " + emailaddress + " activity: " + actid);
        }

        return contactId;
    }

    static public boolean isSuppressedEmail(TablesAdapter db, EmailAddress email) throws OperatingContextException {
        if (email == null)
            return false;

        String emailaddress = email.toStringForIndex();

        return EmailActivityUtil.isSuppressedEmail(db, emailaddress);
    }

    static public boolean isSuppressedEmail(TablesAdapter db, String emailaddress) throws OperatingContextException {
        if (StringUtil.isEmpty(emailaddress))
            return false;

        String contactId = Struct.objectToString(db.firstInIndex("dcmContactEmail", "dcmIndexAddress", emailaddress, false));

        if (StringUtil.isNotEmpty(contactId)) {
            Object supat = db.getScalar("dcmContactEmail", contactId, "dcmSuppressedAt");

            return (supat != null);
        }

        return false;
    }
}
