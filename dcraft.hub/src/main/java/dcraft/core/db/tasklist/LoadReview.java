package dcraft.core.db.tasklist;

import dcraft.db.ICallContext;
import dcraft.db.proc.IStoredProc;
import dcraft.db.request.query.SelectFields;
import dcraft.db.request.update.DbRecordRequest;
import dcraft.db.request.update.UpdateRecordRequest;
import dcraft.db.tables.TableUtil;
import dcraft.db.tables.TablesAdapter;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
import dcraft.hub.op.OperationOutcomeStruct;
import dcraft.struct.RecordStruct;
import dcraft.util.TimeUtil;

public class LoadReview implements IStoredProc {
	@Override
	public void execute(ICallContext request, OperationOutcomeStruct callback) throws OperatingContextException {
		RecordStruct data = request.getDataAsRecord();
		TablesAdapter db = TablesAdapter.ofNow(request);

		SelectFields selectFields = SelectFields.select()
				.with("dcReviewStatus", "Status")
				.with("dcReviewMessage", "Message")
				.with("dcApprovedBy", "ApprovedBy")
				.with("dcApprovedAt", "ApprovedAt");

		callback.returnValue(
				TableUtil.getRecord(db, OperationContext.getOrThrow(), "dcTaskList", data.getFieldAsString("Id"), selectFields)
		);

		callback.returnEmpty();
	}
}
