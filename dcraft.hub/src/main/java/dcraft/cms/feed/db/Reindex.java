package dcraft.cms.feed.db;

import dcraft.db.ICallContext;
import dcraft.db.proc.IStoredProc;
import dcraft.db.tables.TablesAdapter;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationOutcomeStruct;
import dcraft.service.ServiceHub;
import dcraft.service.ServiceRequest;
import dcraft.struct.ListStruct;
import dcraft.struct.RecordStruct;
import dcraft.struct.Struct;

public class Reindex implements IStoredProc {
	@Override
	public void execute(ICallContext request, OperationOutcomeStruct callback) throws OperatingContextException {
		TablesAdapter db = TablesAdapter.ofNow(request);

		String feed = request.getDataAsRecord().getFieldAsString("Feed");

		//System.out.println("f: " + feed);

		// TODO remove old index - first collect all current paths, then subtract real files, then delete

		// TODO index recursive

		ServiceHub.call(
				ServiceRequest.of("dcCoreServices", "Vaults", "ListFiles")
						.withData(RecordStruct.record()
								.with("Vault", "Feeds")
								.with("Path", "/" + feed)
						)
						.withOutcome(
								new OperationOutcomeStruct() {
									@Override
									public void callback(Struct result) throws OperatingContextException {
										if (this.isNotEmptyResult()) {
											ListStruct list = (ListStruct) result;

											for (int i = 0; i < list.size(); i++) {
												FeedUtilDb.update(request.getInterface(), db, "/" + feed + "/" + list.getItemAsRecord(i).selectAsString("FileName"));
											}
										}

										callback.returnEmpty();
									}
								})
		);
	}
}
