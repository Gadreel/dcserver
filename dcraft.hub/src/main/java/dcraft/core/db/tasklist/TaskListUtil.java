package dcraft.core.db.tasklist;

import dcraft.db.request.update.DbRecordRequest;
import dcraft.db.request.update.InsertRecordRequest;
import dcraft.db.tables.TableUtil;
import dcraft.db.tables.TablesAdapter;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
import dcraft.struct.ListStruct;
import dcraft.struct.RecordStruct;
import dcraft.util.StringUtil;
import dcraft.util.TimeUtil;

public class TaskListUtil {

    static public String addTaskListRecord(TablesAdapter db, RecordStruct data) throws OperatingContextException {
        DbRecordRequest req = InsertRecordRequest.insert()
                .withTable("dcTaskList")
                .withConditionallySetFields(data, "Title", "dcTitle",
                        "Description", "dcDescription", "Weight", "dcWeight", "Params", "dcParams", "DebugLevel", "dcDebugLevel")
                .withUpdateField("dcReviewStatus", "Waiting")
                .withUpdateField("dcAddedBy", OperationContext.getOrThrow().getUserContext().getUserId())
                .withUpdateField("dcAddedAt", TimeUtil.now());

        String id = TableUtil.updateRecord(db, req);

        if (data.isNotFieldEmpty("LeadTab")) {
            RecordStruct tab = data.getFieldAsRecord("LeadTab");

            String path = tab.getFieldAsString("Path");

            if (StringUtil.isNotEmpty(path)) {
                tab.with("Path", path.replace("$Id", id));
            }

            db.updateStaticScalar("dcTaskList", id, "dcLeadTabOption", tab);
        }

        if (data.isNotFieldEmpty("ChildTab")) {
            RecordStruct tab = data.getFieldAsRecord("ChildTab");

            String path = tab.getFieldAsString("Path");

            if (StringUtil.isNotEmpty(path)) {
                tab.with("Path", path.replace("$Id", id));
            }

            db.updateStaticScalar("dcTaskList", id, "dcChildTabOption", tab);
        }

        ListStruct steps = data.getFieldAsList("Steps");

        if (steps != null) {
            for (int i = 0; i < steps.size(); i++) {
                RecordStruct step = steps.getItemAsRecord(i);

                String sid = TaskListUtil.addTaskListRecord(db, step);

                db.updateStaticList("dcTaskList", id, "dcStepTask", StringUtil.leftPad("" + i, 4, '0'), sid);
            }
        }

        return id;
    }
}
