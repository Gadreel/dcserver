package dcraft.service;


import dcraft.db.DbServiceRequest;
import dcraft.db.IConnectionManager;
import dcraft.db.util.DbUtil;
import dcraft.hub.ResourceHub;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationOutcomeStruct;
import dcraft.log.Logger;
import dcraft.schema.DbProc;
import dcraft.struct.RecordStruct;
import dcraft.struct.Struct;

/**
 */
public class BaseDataService extends BaseService {
	@Override
	public boolean handle(ServiceRequest request, OperationOutcomeStruct callback) throws OperatingContextException {
		String proccall = request.getName() + "." + request.getFeature() + "." + request.getOp();

		DbProc proc = ResourceHub.getResources().getSchema().getDbProc(proccall);

		// try to handle request as a stored proc call
		// ideally call the DBService, but don't try to validate because validation was done as another service
		// TODO improve this to use possibly remote data services
		if (proc != null) {
			Struct params = request.getData();

			String reqdbname = (params instanceof RecordStruct)
					? ((RecordStruct)params).getFieldAsString("_Database", "default")
					: "default";

			IConnectionManager conn = ResourceHub.getResources().getDatabases().getDatabase(reqdbname);

			if (conn == null) {
				Logger.error("Missing database: " + reqdbname);
				request.getOutcome().returnResult();
				return false;
			}

			return DbUtil.execute((DbServiceRequest) DbServiceRequest.of(request).withOp(proccall), conn);
		}
		
		return false;
	}
}
