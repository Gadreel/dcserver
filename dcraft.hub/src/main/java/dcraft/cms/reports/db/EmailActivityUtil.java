package dcraft.cms.reports.db;

import dcraft.cms.thread.db.email.IEmailActivityForThreadCustomReporter;
import dcraft.db.request.update.DbRecordRequest;
import dcraft.db.request.update.InsertRecordRequest;
import dcraft.db.tables.TableUtil;
import dcraft.db.tables.TablesAdapter;
import dcraft.hub.ResourceHub;
import dcraft.hub.op.OperatingContextException;
import dcraft.log.Logger;
import dcraft.mail.MailUtil;
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

                if (handler instanceof IEmailActivityForThreadCustomReporter) {
                    RecordStruct handlerData =  Struct.objectToRecord(db.getScalar("dcmOutboundEmailActivity", id, "dcmHandlerData"));

                    return ((IEmailActivityForThreadCustomReporter) handler).reportReceived(id, auditkey, reportData, handlerData);
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

    static public String suppressEmail(TablesAdapter db, String email, String actid) throws OperatingContextException {
        String emailaddress = MailUtil.indexableEmailAddress(email);

        if (StringUtil.isEmpty(emailaddress)) {
            Logger.info("Email address unable to suppress: " + email + " activity: " + actid);
            return null;
        }

        String supid = Struct.objectToString(db.firstInIndex("dcmEmailSuppressionList", "dcmEmailAddress", emailaddress, false));

        if (StringUtil.isEmpty(supid)) {
            DbRecordRequest req = InsertRecordRequest.insert()
                    .withTable("dcmEmailSuppressionList")
                    .withUpdateField("dcmEmailAddress", emailaddress)
                    .withUpdateField("dcmAddAt", TimeUtil.now());

            supid = TableUtil.updateRecord(db, req);
        }

        Logger.info("Email address suppressed: " + emailaddress + " activity: " + actid);

        if (StringUtil.isNotEmpty(actid)) {
            db.updateList("dcmEmailSuppressionList", supid, "dcmEmailActivityId", actid, actid);
        }

        return supid;
    }

    static public String unsuppressEmail(TablesAdapter db, String email, String actid) throws OperatingContextException {
        String emailaddress = MailUtil.indexableEmailAddress(email);

        if (StringUtil.isEmpty(emailaddress)) {
            Logger.info("Email address unable to unsuppress: " + email + " activity: " + actid);
            return null;
        }

        String supid = Struct.objectToString(db.firstInIndex("dcmEmailSuppressionList", "dcmEmailAddress", emailaddress, false));

        if (StringUtil.isNotEmpty(supid)) {
            db.deleteRecord("dcmEmailSuppressionList", supid);

            Logger.info("Email address unsuppressed: " + emailaddress + " activity: " + actid);
        }

        return supid;
    }
}
