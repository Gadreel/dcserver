package dcraft.cms.feed.core;

import dcraft.db.request.DataRequest;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationOutcomeEmpty;
import dcraft.hub.op.OperationOutcomeStruct;
import dcraft.service.ServiceHub;
import dcraft.struct.RecordStruct;
import dcraft.struct.Struct;

// TODO abstract so that other data stores may be used instead of dcDb
public class DataStore {
	static public void updateFeed(RecordStruct params, OperationOutcomeEmpty cb) throws OperatingContextException {
		ServiceHub.call(DataRequest.of("dcmFeedUpdate")
				.toServiceRequest()
				.withData(params)
				.withOutcome(new OperationOutcomeStruct() {
					@Override
					public void callback(Struct result) throws OperatingContextException {
						cb.returnEmpty();
					}
				})
		);
	}
	
	static public void deleteFeed(String path, OperationOutcomeEmpty cb) throws OperatingContextException {
		ServiceHub.call(DataRequest.of("dcmFeedDelete")
				.withParam("Path", path)
				.toServiceRequest()
				.withOutcome(new OperationOutcomeStruct() {
					@Override
					public void callback(Struct result) throws OperatingContextException {
						cb.returnEmpty();
					}
				})
		);
	}
}
