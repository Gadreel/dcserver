package dcraft.mail;

import dcraft.cms.thread.db.ThreadUtil;
import dcraft.db.BasicRequestContext;
import dcraft.db.request.update.DbRecordRequest;
import dcraft.db.request.update.InsertRecordRequest;
import dcraft.db.request.update.UpdateRecordRequest;
import dcraft.db.tables.TableUtil;
import dcraft.db.tables.TablesAdapter;
import dcraft.filestore.CommonPath;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
import dcraft.hub.op.OperationOutcomeRecord;
import dcraft.hub.op.UserContext;
import dcraft.log.Logger;
import dcraft.mail.sender.AwsSimpleMailServiceHttpWork;
import dcraft.mail.sender.AwsUtil;
import dcraft.script.Script;
import dcraft.script.StackUtil;
import dcraft.struct.BaseStruct;
import dcraft.struct.ListStruct;
import dcraft.struct.RecordStruct;
import dcraft.struct.Struct;
import dcraft.struct.scalar.DateTimeStruct;
import dcraft.struct.scalar.StringStruct;
import dcraft.task.*;
import dcraft.util.IOUtil;
import dcraft.util.RndUtil;
import dcraft.util.StringUtil;
import dcraft.util.TimeUtil;
import dcraft.web.md.MarkdownUtil;
import dcraft.web.ui.inst.Html;
import dcraft.xml.XElement;

import java.io.BufferedReader;
import java.io.StringReader;
import java.nio.file.Path;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CommUtil {
    static public String ensureCommTrack(String channel, String address, String userid) throws OperatingContextException {
        if (StringUtil.isEmpty(channel) || StringUtil.isEmpty(address))
            return null;

        String normaddr = address;
        String idxaddr = address;

        // TODO future - make address normalization configurable

        if ("email".equals(channel)) {
            normaddr = MailUtil.normalizeEmailAddress(normaddr);
            idxaddr = MailUtil.indexableEmailAddress(idxaddr);
        }

        String chaddr = channel + ":" + idxaddr;

        TablesAdapter db = TablesAdapter.of(BasicRequestContext.ofDefaultDatabase());

        String trackid = Struct.objectToString(db.firstInIndex("dccCommTracker", "dccChannelAddress", chaddr, false));

        if (StringUtil.isNotEmpty(trackid)) {
            if (StringUtil.isNotEmpty(userid)) {
                Object usr = db.getList("dccCommTracker", trackid, "dccUser", userid);

                if (usr == null) {
                    db.updateList("dccCommTracker", trackid, "dccUserId", userid, userid);
                    db.updateList("dccCommTracker", trackid, "dccUserStamp", userid, TimeUtil.now());
                }
            }

            return trackid;
        }

        ZonedDateTime stamp = TimeUtil.now();
        String now = TimeUtil.stampFmt.format(stamp);

        DbRecordRequest request = InsertRecordRequest.insert()
                .withTable("dccCommTracker")
                .withUpdateField("dccChannel", channel)
                .withUpdateField("dccAddress", normaddr)
                .withUpdateField("dccChannelAddress", chaddr)
                .withUpdateField("dccDisplayAddress", address)
                .withUpdateField("dccStatus", "Unconfirmed")
                // auditing
                .withUpdateField("dccHistoryStamp", now, now)
                .withUpdateField("dccHistoryStatus", now, "Unconfirmed")
                .withUpdateField("dccHistoryUser", now, OperationContext.getOrThrow().getUserContext().getUserId())
                .withUpdateField("dccHistoryNote", now, "address added");

        if (StringUtil.isNotEmpty(userid))
            request.withUpdateField("dccUser", userid, userid);

        return TableUtil.updateRecord(db, request);
    }

    /*
        return list of records:
        {
            "Name": [name]              // optional
            "Address": [address]
            "TrackerId": [commtracker id]
            "TrackerStatus": [commtracker status]
        }
     */
    static public ListStruct prepareAddresses(String channel, String addresses) throws OperatingContextException {
        ListStruct result = ListStruct.list();

        if (StringUtil.isEmpty(channel) || StringUtil.isEmpty(addresses))
            return result;

        // TODO future - make address prep configurable

        if ("email".equals(channel)) {
            List<EmailAddress> emails = EmailAddress.parseList(addresses);

            if (emails != null) {
                TablesAdapter db = TablesAdapter.of(BasicRequestContext.ofDefaultDatabase());

                for (EmailAddress email : emails) {
                    String address = email.getMailAddress().getAddress();

                    String trackid = CommUtil.ensureCommTrack(channel, address, null);

                    String personal = email.getPersonal();

                    if (StringUtil.isEmpty(personal)) {
                        List<String> uids = db.getListKeys("dccCommTracker", trackid, "dccUserId");
                        ZonedDateTime recentstamp = ZonedDateTime.of(1,1,1,0,0,0,0,ZoneId.of("UTC"));
                        String recentuid = null;

                        for (String uid : uids) {
                            ZonedDateTime added = Struct.objectToDateTime(db.getList("dccCommTracker", trackid, "dccUserStamp", uid));

                            if (added.compareTo(recentstamp) > 0) {
                                recentstamp = added;
                                recentuid = uid;
                            }
                        }

                        if (StringUtil.isNotEmpty(recentuid)) {
                            personal = Struct.objectToString(db.getScalar("dcUser", recentuid, "dcFirstName")) + " "
                                    + Struct.objectToString(db.getScalar("dcUser", recentuid, "dcLastName"));
                        }
                    }

                    result.with(new RecordStruct()
                            .with("Name", personal)          // we don't need the encoded personal, AWS should be able to do that?
                            .with("Address", address)
                            .with("TrackerId", trackid)
                            .with("TrackerStatus", db.getScalar("dccCommTracker", trackid, "dccStatus"))
                    );
                }
            }
        }

        return result;
    }

    /*
        return list of records:
        {
            "Name": [name]              // optional
            "Address": [address]
            "TrackerId": [commtracker id]
        }
     */
    static public ListStruct prepareAddresses(String channel, ListStruct addresses) throws OperatingContextException {
        ListStruct result = ListStruct.list();

        if (StringUtil.isEmpty(channel) || (addresses == null))
            return result;

        // TODO future - make address prep configurable

        if ("email".equals(channel)) {
            for (int i = 0; i < addresses.size(); i++) {
                String addrline = addresses.getItemAsString(i);

                result.withCollection(CommUtil.prepareAddresses(channel, addrline));
            }
        }

        return result;
    }

    /*
        this is segmented, not conversational, so a separate send for each address

        TODO - return enhanced list with SendId added to each
     */
    static public ListStruct prepareSend(String channel, String path, ListStruct addresses, BaseStruct args, ListStruct tags) throws OperatingContextException {
        if (StringUtil.isEmpty(channel) || (addresses == null) || addresses.isEmpty())
            return addresses;

        TablesAdapter db = TablesAdapter.of(BasicRequestContext.ofDefaultDatabase());

        // TODO future - make queue prep configurable

        if ("email".equals(channel)) {
            for (int i = 0; i < addresses.size(); i++) {
                RecordStruct address = addresses.getItemAsRecord(i);

                String uuid = RndUtil.nextUUId();

                ZonedDateTime stamp = TimeUtil.now();
                String now = TimeUtil.stampFmt.format(stamp);

                String activitytype = address.getFieldAsString("PlanType");
                String activitynote = address.getFieldAsString("PlanNote");

                if (StringUtil.isEmpty(activitytype)) {
                    Logger.error("Missing send plan for: " + address.getField("TrackerId") + " - send not queued");
                    continue;
                }

                if (StringUtil.isEmpty(activitynote))
                    activitynote = "prepared for queue";

                DbRecordRequest request = InsertRecordRequest.insert()
                        .withTable("dccCommSend")
                        .withUpdateField("dccUuid", uuid)
                        .withUpdateField("dccTrackerId", address.getFieldAsString("TrackerId"), address.getField("TrackerId"))
                        .withUpdateField("dccTrackerDisplayName", address.getFieldAsString("TrackerId"), address.getField("Name"))
                        .withUpdateField("dccQueuedAt", now)
                        .withUpdateField("dccTrackerStatus", address.getFieldAsString("TrackerId"), activitytype)
                        .withUpdateField("dccPath", path)
                        .withUpdateField("dccArgs", args)
                        // auditing
                        .withUpdateField("dccActivityStamp", now, now)
                        .withUpdateField("dccActivityType", now, activitytype)
                        .withUpdateField("dccActivityNote", now, activitynote);

                // TODO support tags

                //if (StringUtil.isNotEmpty(userid))
                //    request.withUpdateField("dccUser", userid, userid);

                address.with("SendId", TableUtil.updateRecord(db, request));
            }
        }

        return addresses;
    }

    static public void buildContent(String channel, RecordStruct params, OperationOutcomeRecord callback) throws OperatingContextException {
        if (StringUtil.isEmpty(channel)) {
            callback.returnEmpty();
            return;
        }

        // TODO future - make build content configurable

        if ("email".equals(channel)) {
            TaskHub.submit(
                    Task.ofSubContext()
                            .withTitle("Email content builder")
                            .withParams(params)
                            .withWork(new EmailRequestWork()),
                    new TaskObserver() {
                        @Override
                        public void callback(TaskContext task) {
                            RecordStruct resp = Struct.objectToRecord(task.getResult());

                            callback.returnValue(resp);
                        }
                    }
            );
        }
    }

    /*
        {
            SendId: nnnn
            Request: {
                To:
                Path:
                Args:
            }
        }
     */

    static public void deliver(String channel, RecordStruct params, OperationOutcomeRecord callback) throws OperatingContextException {
        if (StringUtil.isEmpty(channel)) {
            callback.returnEmpty();
            return;
        }

        TablesAdapter db = TablesAdapter.of(BasicRequestContext.ofDefaultDatabase());

        // TODO future - make build content configurable

        if ("email".equals(channel)) {
            IWork sender = AwsUtil.buildSendEmailWork(params);

            if (sender != null) {
                TaskHub.submit(
                        Task.ofSubContext()
                                .withTitle("Email content builder")
                                .withParams(params)
                                .withWork(sender), new TaskObserver() {
                                    @Override
                                    public void callback(TaskContext task) {
                                        RecordStruct resp = Struct.objectToRecord(task.getResult());

                                        System.out.println("trans: " + resp.getField("Transport"));

                                        try {
                                            ZonedDateTime stamp = TimeUtil.now();
                                            String now = TimeUtil.stampFmt.format(stamp);

                                            String msgid = resp.selectAsString("Transport.MessageId");

                                            DbRecordRequest request = UpdateRecordRequest.update()
                                                    .withTable("dccCommSend")
                                                    .withId(params.getFieldAsString("SendId"))
                                                    .withUpdateField("dccActivityStamp", now, now);

                                            if (task.hasExitErrors()) {
                                                request
                                                        .withUpdateField("dccActivityType", now, "Attempted");
                                            }
                                            else {
                                                request
                                                        .withUpdateField("dccMessageId", msgid)
                                                        .withUpdateField("dccSendIndex", "email:" + msgid)
                                                        .withUpdateField("dccActivityType", now, "Sent");

                                                List<String> trackerids = db.getListKeys("dccCommSend", params.getFieldAsString("SendId"), "dccTrackerId");

                                                // we assume all comm addresses listed are included in the send

                                                if (trackerids != null) {
                                                    for (String tid : trackerids) {
                                                        request.withUpdateField("dccTrackerStatus", tid, "Sent");
                                                    }
                                                }
                                            }

                                            TableUtil.updateRecord(db, request);

                                            callback.returnValue(resp);
                                        }
                                        catch (OperatingContextException x) {
                                            Logger.error("Unable to deliver out of task context");
                                        }

                                        callback.returnEmpty();
                                    }
                                });
            }
            else {
                Logger.error("sender work not available for: " + channel);
                callback.returnEmpty();
            }
        }
    }

    static public void reviewSend(String sendid, String actstamp, String reportid) throws OperatingContextException {
        TablesAdapter db = TablesAdapter.of(BasicRequestContext.ofDefaultDatabase());

        if (StringUtil.isNotEmpty(sendid) && StringUtil.isNotEmpty(actstamp)) {
            ZonedDateTime reportat = Struct.objectToDateTime(db.getList("dccCommSend", sendid, "dccActivityStamp", actstamp));
            String reporttype = Struct.objectToString(db.getList("dccCommSend", sendid, "dccActivityType", actstamp));
            ListStruct reporttargets = Struct.objectToList(db.getList("dccCommSend", sendid, "dccActivityReportTargets", actstamp));

            if (reporttargets != null) {
                for (int i = 0; i < reporttargets.size(); i++) {
                    String trackid = reporttargets.getItemAsString(i);

                    // record status for the send per comm tracker

                    ZonedDateTime latestact = Struct.objectToDateTime(db.getScalar("dccCommTracker", trackid, "dccLatestActivity"));

                    // if our send is the latest report for this, apply the report

                    if ((latestact == null) || (latestact.compareTo(reportat) <= 0)) {
                        //String status = Struct.objectToString(db.getList("dccCommSend", sendid, "dccTrackerStatus", trackid));

                        if ("Delivery".equals(reporttype)) {
                            db.setList("dccCommSend", sendid, "dccTrackerStatus", trackid, "Delivery");
                        }
                        else if ("Bounce".equals(reporttype)) {
                            db.setList("dccCommSend", sendid, "dccTrackerStatus", trackid, "Bounce");
                        }
                        else if ("Complaint".equals(reporttype)) {
                            db.setList("dccCommSend", sendid, "dccTrackerStatus", trackid, "Complaint");
                        }

                        db.setList("dccCommSend", sendid, "dccTrackerLatestActivity", trackid, reportat);
                    }

                    // record suppression / unsuppression for comm tracker

                    latestact = Struct.objectToDateTime(db.getScalar("dccCommTracker", trackid, "dccLatestActivity"));

                    // if our send is the latest report for this, apply the report
                    if ((latestact == null) || (latestact.compareTo(reportat) <= 0)) {
                        String status = Struct.objectToString(db.getScalar("dccCommTracker", trackid, "dccStatus"));
                        boolean history = false;

                        if ("Delivery".equals(reporttype)) {
                            if (! "Confirmed".equals(status)) {
                                db.setScalar("dccCommTracker", trackid, "dccStatus", "Confirmed");
                                history = true;
                                db.setList("dccCommTracker", trackid, "dccHistoryStatus", actstamp, "Confirmed");
                            }
                        }
                        else if ("Bounce".equals(reporttype) || "Complaint".equals(reporttype)) {
                            if (! "ExternalBlocked".equals(status)) {
                                db.setScalar("dccCommTracker", trackid, "dccStatus", "ExternalBlocked");
                                history = true;
                                db.setList("dccCommTracker", trackid, "dccHistoryStatus", actstamp, "ExternalBlocked");
                            }
                        }

                        db.setScalar("dccCommTracker", trackid, "dccLatestActivity", reportat);

                        if (history) {
                            db.setList("dccCommTracker", trackid, "dccHistoryStamp", actstamp, actstamp);
                            db.setList("dccCommTracker", trackid, "dccHistoryUser", actstamp, OperationContext.getOrThrow().getUserContext().getUserId());

                            if (StringUtil.isNotEmpty(reportid))
                                db.setList("dccCommTracker", trackid, "dccHistoryReportId", actstamp, reportid);
                        }
                    }
                }
            }
        }
    }
}
