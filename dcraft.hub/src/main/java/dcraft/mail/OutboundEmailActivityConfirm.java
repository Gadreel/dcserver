package dcraft.mail;

import dcraft.db.ICallContext;
import dcraft.db.proc.IStoredProc;
import dcraft.db.tables.TablesAdapter;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationOutcomeStruct;
import dcraft.log.Logger;
import dcraft.service.portable.PortableMessageUtil;
import dcraft.sql.SqlConnection;
import dcraft.sql.SqlUtil;
import dcraft.sql.SqlWriter;
import dcraft.struct.RecordStruct;
import dcraft.tool.sentinel.EmailActivityProcessWork;
import dcraft.util.StringUtil;
import dcraft.util.TimeUtil;

import java.time.ZonedDateTime;

public class OutboundEmailActivityConfirm implements IStoredProc {
	@Override
	public void execute(ICallContext request, OperationOutcomeStruct callback) throws OperatingContextException {
		RecordStruct data = request.getDataAsRecord();

		try (SqlConnection conn = SqlUtil.getConnection("sentinel-readwrite")) {
			SqlWriter updateEmailActivity = SqlWriter.update("dca_email_activity", data.getFieldAsString("ReportId"))
					.with("ReportStatus", 3)
					.withConditional("ConfirmId", data.getFieldAsString("ActivityId"))
					.with("ConfirmAt", TimeUtil.now())
					.withConditional("Note", data.getFieldAsString("Note"));

			conn.executeWrite(updateEmailActivity);
		}
		catch (Exception x) {
			Logger.error("Unable to record that report message queued.");
		}

		callback.returnEmpty();
	}
}
