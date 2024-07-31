package dcraft.core.activity;

import dcraft.db.BasicRequestContext;
import dcraft.db.request.update.DbRecordRequest;
import dcraft.db.request.update.InsertRecordRequest;
import dcraft.db.tables.TableUtil;
import dcraft.db.tables.TablesAdapter;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
import dcraft.log.Logger;
import dcraft.mail.CommUtil;
import dcraft.struct.RecordStruct;
import dcraft.util.StringUtil;
import dcraft.util.TimeUtil;

import java.time.ZonedDateTime;

public class ActivityUtil {
    // leave userid null if you don't know the user
    static public String recordUserActivity(String channel, String address, String userid, String target, String contextname, RecordStruct contextdata, String note) throws OperatingContextException {
        if (StringUtil.isEmpty(channel) || StringUtil.isEmpty(address))
            return null;

        String trackid = CommUtil.ensureCommTrack(channel, address, userid);

        if (trackid == null)
            return null;

        TablesAdapter db = TablesAdapter.of(BasicRequestContext.ofDefaultDatabase());

        ZonedDateTime stamp = TimeUtil.now();

        DbRecordRequest request = InsertRecordRequest.insert()
                .withTable("dcUserActivity")
                .withUpdateField("dcTracking", trackid)
                .withUpdateField("dcBy", OperationContext.getOrThrow().getUserContext().getUserId())
                .withUpdateField("dcAt", stamp)
                .withUpdateField("dcTarget", target)
                .withUpdateField("dcContext", contextname)
                .withUpdateField("dcOrigin", OperationContext.getOrThrow().getOrigin())
                .withUpdateField("dcOpId", OperationContext.getOrThrow().getOpId());

        if (contextdata != null)
            request.withUpdateField("dcContextData", contextdata);

        if (StringUtil.isNotEmpty(note))
            request.withUpdateField("dcNote", note);

        String actid = TableUtil.updateRecord(db, request);

        Logger.info("User Activity " + trackid + " - " + userid + " - " + target + " - " + contextname, "UserActivity", actid);

        return actid;
    }
}
