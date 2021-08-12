package dcraft.cms.thread.db;

import dcraft.db.ICallContext;
import dcraft.db.proc.IStoredProc;
import dcraft.db.tables.TablesAdapter;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationOutcomeStruct;
import dcraft.struct.RecordStruct;

public class ChangeFolder implements IStoredProc {
	@Override
	public void execute(ICallContext request, OperationOutcomeStruct callback) throws OperatingContextException {
		RecordStruct data = request.getDataAsRecord();
		
		TablesAdapter db = TablesAdapter.of(request);

		String id = ThreadUtil.getThreadId(db, data);
		
		String party = data.getFieldAsString("Party");
		String folder = data.getFieldAsString("Folder");
		Boolean read = data.getFieldAsBoolean("Read");

		ThreadUtil.updateFolder(db, id, party, folder, read);

		callback.returnEmpty();
	}
}

/*
dc.db.database.Select({
			Table: 'dcmThread',
			Select: [
				{
					Field: 'Id'
				},
				{
					Field: 'dcmFolder'
				}
			],
			Compact: false,
			Where: {
				Expression: 'Contains',
				A: {
					Field: 'dcmFolder'
				},
				B: {
					Value: '/Sent'
				}
			}
		 },
		 {
		 	callback: function(res) {
				console.log('found: ' + res.length);
				
				for (var i = 0; i < res.length; i++) {
					for (var n = 0; n < res[i].dcmFolder.length; n++) {
						if (res[i].dcmFolder[n].Data == '/Sent') {
							console.log('party: ' + res[i].dcmFolder[n].SubId + ' - ' + res[i].Id.Data);
							
							dc.comm.sendTestMessage({
								Service: 'dcmServices',
								Feature: 'Thread',
								Op: 'ChangeFolder',
								Body: {
									Id: res[i].Id.Data,
									Party: res[i].dcmFolder[n].SubId,
									Folder: '/Archive'
								}
							})
						}
					}
				}
			}
		})
 */