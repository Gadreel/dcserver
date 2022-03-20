package dcraft.service;


import dcraft.db.DatabaseException;
import dcraft.db.DbServiceRequest;
import dcraft.db.IConnectionManager;
import dcraft.db.util.DbUtil;
import dcraft.filestore.CommonPath;
import dcraft.hub.ResourceHub;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
import dcraft.hub.op.OperationOutcomeStruct;
import dcraft.log.Logger;
import dcraft.schema.DbProc;
import dcraft.script.Script;
import dcraft.script.StackUtil;
import dcraft.struct.BaseStruct;
import dcraft.struct.RecordStruct;
import dcraft.struct.Struct;
import dcraft.struct.scalar.StringStruct;
import dcraft.task.ChainWork;
import dcraft.task.TaskHub;
import dcraft.util.StringUtil;
import dcraft.util.web.DateParser;
import dcraft.web.HttpDestStream;

import java.nio.file.Path;

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
			BaseStruct params = request.getData();

			String reqdbname = (params instanceof RecordStruct)
					? ((RecordStruct)params).getFieldAsString("_Database", "default")
					: "default";

			IConnectionManager conn = ResourceHub.getResources().getDatabases().getDatabase(reqdbname);

			if (conn == null) {
				Logger.error("Missing database: " + reqdbname);
				request.getOutcome().returnResult();
				return false;
			}

			try {
				return DbUtil.execute((DbServiceRequest) DbServiceRequest.of(request).withOp(proccall), conn);
			}
			catch (DatabaseException x) {
				Logger.error("Error with database routine: " + x);
				request.getOutcome().returnResult();
				return false;
			}
		}
		else {
			String path = "/services/" + request.getName() + "/" + request.getFeature() + "/" + request.getOp() + ".dcs.xml";

			Path fnd = ResourceHub.getResources().getScripts().findScript(CommonPath.from(path));

			OperationContext ctx = OperationContext.getOrThrow();

			if (fnd != null) {
				Script script = Script.of(fnd);

				ctx.addVariable("Data", request.getData());

				TaskHub.submit(
						ChainWork.of(taskctx -> {
								//System.out.println("before");

								taskctx.returnEmpty();
							})
							.then(script.toWork())
							.then(taskctx -> {
								//System.out.println("after 1: " + taskctx.getParams());
								//System.out.println("after 2: " + taskctx.getResult());

								callback.returnValue(taskctx.getParams());
								taskctx.returnEmpty();
							})
				);

				return true;
			}
		}
		
		return false;
	}
}
