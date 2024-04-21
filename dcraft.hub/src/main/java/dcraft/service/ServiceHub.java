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
package dcraft.service;

import dcraft.filestore.CommonPath;
import dcraft.filestore.local.LocalStore;
import dcraft.filevault.DepositHub;
import dcraft.filevault.work.BuildDepositWork;
import dcraft.hub.ResourceHub;
import dcraft.hub.app.ApplicationHub;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationOutcomeStruct;
import dcraft.log.Logger;
import dcraft.log.count.CountHub;
import dcraft.schema.SchemaResource;
import dcraft.service.work.FileQueuePollWork;
import dcraft.struct.RecordStruct;
import dcraft.task.*;
import dcraft.task.queue.QueueHub;
import dcraft.task.run.WorkHub;
import dcraft.task.run.WorkTopic;
import dcraft.util.StringUtil;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

public class ServiceHub {
	final static public LocalStore MessageStore = LocalStore.of(ApplicationHub.getDeploymentNodePath().resolve("messages"));

	static public boolean call(IServiceRequestBuilder request, OperationOutcomeStruct callback) throws OperatingContextException {
		return ServiceHub.call(request.toServiceRequest().withOutcome(callback));
	}

	static public boolean call(IServiceRequestBuilder request) throws OperatingContextException {
		return ServiceHub.call(request.toServiceRequest());
	}
	
	static public boolean call(ServiceRequest request) throws OperatingContextException {
		OperationOutcomeStruct callback = request.requireOutcome();

		try {
			SchemaResource.OpInfo opInfo = ResourceHub.getResources().getServices().lookupOp(request);

			request.def = opInfo;
		}
		catch(Exception x) {
			Logger.error("Unhandled exception in service: " + request.getName() + " - " + x);
			callback.returnEmpty();
			return false;
		}

		// if validate failed, there will be errors
		if (! request.validate()) {
			callback.returnEmpty();
			return false;
		}
		
		// message was sent, record it here
		CountHub.countObjects("dcBusMessageSent", request.getName());
		
        if (Logger.isDebug())
        	Logger.debug("Message being handled for : " + request.getName());
		
        try {
        	// modern services don't go through resource hub
        	if (request.getPath() != null)
        		return ServiceResource.STANDARD_SERVICE.handle(request, request.getOutcome());

			return ResourceHub.getResources().getServices().handle(request);
        }
		catch(Exception x) {	
			Logger.error("Unhandled exception in service: " + request.getName() + " - " + x);
			callback.returnEmpty();
			return false;
		}
    }

    // message queue provides a way for

	static public void enableMessageChecker() {
		/* Maybe automate someday, for now needs a message to check it
		Task peroidicChecker = Task.ofHubRoot()
				.withTitle("Review local service (msg) queue")
				.withTopic(WorkTopic.SYSTEM)
				.withNextId("QUEUE")
				.withWork(new IWork() {
						@Override
						public void run(TaskContext taskctx) throws OperatingContextException {
							FileQueuePollWork work = FileQueuePollWork.ofRepeat();

							taskctx.resumeWith(work);
						}
					}
				);

		TaskHub.scheduleIn(peroidicChecker, 5);		// TODO switch to 110

		Task startChecker = Task.ofHubRoot()
				.withTitle("Start periodic review of service (msg) queue")
				.withTopic(WorkTopic.SYSTEM)
				.withNextId("QUEUE")
				.withWork(new IWork() {
					@Override
					public void run(TaskContext taskctx) throws OperatingContextException {
						// sweep the triggers folder every 5 seconds
						TaskHub.scheduleEvery(peroidicChecker, 5);
						taskctx.returnEmpty();
					}
				});

		TaskHub.scheduleIn(startChecker, 60);			// TODO switch to 150

		 */
	}
}
