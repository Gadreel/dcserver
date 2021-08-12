package dcraft.core.db.tasklist;

import dcraft.db.ICallContext;
import dcraft.db.proc.IStoredProc;
import dcraft.db.request.update.DbRecordRequest;
import dcraft.db.request.update.UpdateRecordRequest;
import dcraft.db.tables.TableUtil;
import dcraft.db.tables.TablesAdapter;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
import dcraft.hub.op.OperationOutcomeStruct;
import dcraft.struct.RecordStruct;
import dcraft.util.TimeUtil;

public class UpdateReview implements IStoredProc {
	@Override
	public void execute(ICallContext request, OperationOutcomeStruct callback) throws OperatingContextException {
		RecordStruct data = request.getDataAsRecord();
		TablesAdapter db = TablesAdapter.of(request);

		String id = data.getFieldAsString("Id");

		DbRecordRequest req = UpdateRecordRequest.update()
				.withTable("dcTaskList")
				.withId(id)
				.withConditionallySetFields(data, "Status", "dcReviewStatus", "Message", "dcReviewMessage");

		if (data.isNotFieldEmpty("Approved")) {
			if (data.getFieldAsBooleanOrFalse("Approved"))
				req
						.withUpdateField("dcApprovedBy", OperationContext.getOrThrow().getUserContext().getUserId())
						.withUpdateField("dcApprovedAt", TimeUtil.now());
			else
				req
						.withRetireField("dcApprovedBy")
						.withRetireField("dcApprovedAt");
		}

		TableUtil.updateRecord(db, req);

		callback.returnEmpty();
	}
}
