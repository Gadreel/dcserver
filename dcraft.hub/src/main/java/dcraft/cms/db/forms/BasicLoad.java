package dcraft.cms.db.forms;

import dcraft.db.ICallContext;
import dcraft.db.proc.IStoredProc;
import dcraft.db.request.query.SelectFields;
import dcraft.db.tables.TableUtil;
import dcraft.db.tables.TablesAdapter;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
import dcraft.hub.op.OperationOutcomeStruct;
import dcraft.struct.RecordStruct;

public class BasicLoad implements IStoredProc {
	@Override
	public void execute(ICallContext request, OperationOutcomeStruct callback) throws OperatingContextException {
		RecordStruct data = request.getDataAsRecord();

		String id = data.getFieldAsString("Id");

		TablesAdapter db = TablesAdapter.of(request);

		RecordStruct rec = TableUtil.getRecord(db, OperationContext.getOrThrow(), "dcmBasicCustomForm", id, SelectFields.select()
				.withAs("Title", "dcmTitle")
				.withAs("Alias", "dcmAlias")
				.withAs("Email", "dcmEmail")
		);

		callback.returnValue(rec);
	}
}
