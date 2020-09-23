package dcraft.cms.common.db;

import dcraft.db.Constants;
import dcraft.db.ICallContext;
import dcraft.db.proc.IStoredProc;
import dcraft.db.tables.TablesAdapter;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationOutcomeRecord;
import dcraft.hub.op.OperationOutcomeStruct;
import dcraft.interchange.icontact.IContactUtil;
import dcraft.service.ServiceHub;
import dcraft.service.ServiceRequest;
import dcraft.struct.ListStruct;
import dcraft.struct.RecordStruct;
import dcraft.struct.Struct;

public class EmailSubscribe implements IStoredProc {
	@Override
	public void execute(ICallContext request, OperationOutcomeStruct callback) throws OperatingContextException {
		RecordStruct data = request.getDataAsRecord();

		//TablesAdapter db = TablesAdapter.ofNow(request);

		// TODO add to system log

		// TODO maybe add to thread notices - or that comes from log event?

		IContactUtil.subscribe(null, RecordStruct.record()
						.with("email", data.getFieldAsString("Email"))
						.with("firstName", data.getFieldAsString("First"))
						.with("lastName", data.getFieldAsString("Last")), null,
				new OperationOutcomeRecord() {
					@Override
					public void callback(RecordStruct result) throws OperatingContextException {
						//if (this.isNotEmptyResult())
						//	System.out.println("got: " + result.toPrettyString());

						callback.returnEmpty();
					}
				});

	}
}
