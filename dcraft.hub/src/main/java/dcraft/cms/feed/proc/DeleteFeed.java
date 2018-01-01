package dcraft.cms.feed.proc;

import dcraft.db.DatabaseException;
import dcraft.db.DbServiceRequest;
import dcraft.db.proc.IStoredProc;
import dcraft.db.request.query.LoadRecordRequest;
import dcraft.db.request.query.SelectFields;
import dcraft.db.request.update.RetireRecordRequest;
import dcraft.db.tables.TablesAdapter;
import dcraft.filestore.CommonPath;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
import dcraft.hub.op.OperationOutcomeStruct;
import dcraft.hub.time.BigDateTime;
import dcraft.log.Logger;
import dcraft.service.ServiceHub;
import dcraft.struct.RecordStruct;
import dcraft.struct.Struct;

import java.time.ZonedDateTime;

public class DeleteFeed implements IStoredProc {
	@Override
	public void execute(DbServiceRequest request, OperationOutcomeStruct callback) throws OperatingContextException {
		RecordStruct params = request.getDataAsRecord();
		
		String path = params.getFieldAsString("Path");
		
		// TODO replicating
		// if (task.isReplicating())
		
		CommonPath opath  = CommonPath.from(path);
		CommonPath ochan = opath.subpath(0, 2);		// site and channel
		
		TablesAdapter db = TablesAdapter.of(request);
		
		BigDateTime when = BigDateTime.nowDateTime();
		Object oid = db.firstInIndex("dcmFeed", "dcmPath", path, when, false);
		
		if (oid != null) {
			ServiceHub.call(new LoadRecordRequest()			// TODO same database as caller
					.withTable("dcmFeed")
					.withId(oid.toString())
					.withSelect(new SelectFields()
							.with("Id")
							.with("dcmPath", "Path")
							.with("dcmPublished", "Published")
					)
					.toServiceRequest()
					.withOutcome(new OperationOutcomeStruct() {
					@Override
					public void callback(Struct result) throws OperatingContextException {
						if ((oid != null) && (result == null)) {
							Logger.error("Unable to update feed - id found but no record loaded");
							callback.returnEmpty();
							return;
						}
						
						OperationContext.getOrThrow().touch();
						
						// delete from dcmFeedIndex too
						ZonedDateTime opubtime = ((RecordStruct) result).getFieldAsDateTime("Published");
					
						try {
							request.getInterface().kill(request.getTenant(), "dcmFeedIndex", ochan, request.getInterface().inverseTime(opubtime), oid);
						}
						catch (DatabaseException x) {
							Logger.error("Error killing global: " + x);
						}
						
						ServiceHub.call(RetireRecordRequest.of("dcmFeed", oid.toString())
							.toServiceRequest().withOutcome(callback));
					}
				})
			);
		}
		else {
			callback.returnEmpty();
		}
	}
}
