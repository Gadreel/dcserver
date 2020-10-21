/* ************************************************************************
#
#  designCraft.io
#
#  http://designcraft.io/
#
#  Copyright:
#    Copyright 2014 eTimeline, LLC. All rights reserved.
#
#  License:
#    See the license.txt file in the project's top-level directory for details.
#
#  Authors:
#    * Andy White
#
************************************************************************ */
package dcraft.hub;

import java.util.Map;

import dcraft.hub.config.LocalHubConfigLoader;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
import dcraft.hub.op.OperationOutcomeStruct;
import dcraft.hub.op.UserContext;
import dcraft.service.ServiceHub;
import dcraft.service.ServiceRequest;
import dcraft.struct.RecordStruct;
import dcraft.struct.Struct;
import dcraft.task.*;

import dcraft.hub.app.ApplicationHub;
import dcraft.log.Logger;

public class Daemon implements dcraft.daemon.IDaemon {
	@Override
	public void start(String instanceName) {
		Map<String, String> env = System.getenv();

		if (! env.containsKey("DC_DEPLOYMENT")) {
			System.err.println("Deployment name is missing");
			System.exit(1);
			return;
		}

		if (! env.containsKey("DC_NODE")) {
			System.err.println("Node id is missing");
			System.exit(1);
			return;
		}

		ApplicationHub.init(env.get("DC_DEPLOYMENT"), env.get("DC_NODE"));

		if (! ApplicationHub.startServer(LocalHubConfigLoader.local())) {
			Logger.error("Unable to continue, hub not properly started, please see logs");
			ApplicationHub.stopServer();
			System.exit(1);
			return;
		}

		Logger.info("Daemon started");
	}

	@Override
	public void handleCommand(String command) {
		Logger.info("Daemon got command: " + command);

		RecordStruct msg = Struct.objectToRecord(command);

		if (msg != null) {
			try {

				OperationContext tctx = OperationContext.context(
						UserContext.rootUser());

				TaskHub.submit(Task.ofContext(tctx)
								.withTitle("Daemon message")
								.withTimeout(5)    // 40 minutes
								.withWork(new IWork() {
									@Override
									public void run(TaskContext taskctx) throws OperatingContextException {
										ServiceHub.call(ServiceRequest.of(msg.getFieldAsString("Op"))
												.withData(msg.getField("Body"))
												.withOutcome(new OperationOutcomeStruct() {
													@Override
													public void callback(Struct result) throws OperatingContextException {
														taskctx.returnEmpty();
													}
												})
										);
									}
								})
				);
			}
			catch (Exception x) {
				Logger.error("Daemon got command but the command failed: " + x);
			}
		}
	}

	@Override
	public void shutdown() {
		Logger.info("Daemon stopping");

		ApplicationHub.stopServer();
	}
}
