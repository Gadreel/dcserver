package dcraft.cms.db.forms;

import dcraft.db.ICallContext;
import dcraft.db.proc.IStoredProc;
import dcraft.db.request.update.DbRecordRequest;
import dcraft.db.request.update.UpdateRecordRequest;
import dcraft.db.tables.TableUtil;
import dcraft.db.tables.TablesAdapter;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationOutcomeStruct;
import dcraft.struct.RecordStruct;
import dcraft.struct.Struct;
import dcraft.util.StringUtil;

import java.util.List;

public class BasicUpdate implements IStoredProc {
	@Override
	public void execute(ICallContext request, OperationOutcomeStruct callback) throws OperatingContextException {
		RecordStruct data = request.getDataAsRecord();
		String id = data.getFieldAsString("Id");

		TablesAdapter db = TablesAdapter.ofNow(request);
		
		DbRecordRequest req = UpdateRecordRequest.update()
				.withTable("dcmBasicCustomForm")
				.withId(id)
				.withConditionallyUpdateFields(data, "Title", "dcmTitle", "Alias", "dcmAlias", "Email", "dcmEmail");

		TableUtil.updateRecord(db, req);

		callback.returnEmpty();
	}
}
