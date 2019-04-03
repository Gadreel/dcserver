package dcraft.cms.db.forms;

import dcraft.db.ICallContext;
import dcraft.db.proc.IStoredProc;
import dcraft.db.request.update.DbRecordRequest;
import dcraft.db.request.update.InsertRecordRequest;
import dcraft.db.tables.TableUtil;
import dcraft.db.tables.TablesAdapter;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationOutcomeStruct;
import dcraft.struct.RecordStruct;
import dcraft.struct.Struct;
import dcraft.util.StringUtil;

import java.util.List;

public class BasicAdd implements IStoredProc {
	@Override
	public void execute(ICallContext request, OperationOutcomeStruct callback) throws OperatingContextException {
		RecordStruct data = request.getDataAsRecord();

		TablesAdapter db = TablesAdapter.ofNow(request);
		
		DbRecordRequest req = InsertRecordRequest.insert()
				.withTable("dcmBasicCustomForm")
				.withConditionallyUpdateFields(data, "Title", "dcmTitle", "Alias", "dcmAlias", "Email", "dcmEmail");

		String id = TableUtil.updateRecord(db, req);

		callback.returnValue(RecordStruct.record()
			.with("Id", id)
		);
	}
}
