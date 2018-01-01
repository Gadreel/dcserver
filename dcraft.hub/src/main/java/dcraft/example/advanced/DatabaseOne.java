package dcraft.example.advanced;

import dcraft.db.request.DataRequest;
import dcraft.example.util.ExampleConfigLoader;
import dcraft.example.work.ChainInfoWork;
import dcraft.hub.app.ApplicationHub;
import dcraft.hub.op.OperationContext;
import dcraft.hub.op.UserContext;
import dcraft.log.Logger;
import dcraft.service.ServiceHub;
import dcraft.struct.RecordStruct;
import dcraft.task.*;

import java.time.ZonedDateTime;

/*
 * test db calls - under construction
 */
public class DatabaseOne {
	public static void main(String... args) throws Exception {
		ApplicationHub.startServer(ExampleConfigLoader.local(DatabaseOne.class));
		
		OperationContext.set(OperationContext.context(UserContext.rootUser()));
		
		// call as service
		
		ServiceHub.call(DataRequest.of("dcCleanup")
				.withParam("ExpireThreshold", ZonedDateTime.now().minusMinutes(3))
				.withParam("LongExpireThreshold", ZonedDateTime.now().minusMinutes(5))
		);
		
		// alternative call in chain
		
		IWork chain = ChainWork
				.of(ChainInfoWork.of("a", 1))
				.then(DataRequest.of("dcCleanup")
						.withParam("ExpireThreshold", ZonedDateTime.now().minusMinutes(3))
						.withParam("LongExpireThreshold", ZonedDateTime.now().minusMinutes(5))
				)
				.then(ChainInfoWork.of("a", 1));
		
		Task task = Task.ofHubRoot()
				.withTitle("Chain Master")
				.withWork(chain);
		
		TaskHub.submit(task, new TaskObserver() {
			@Override
			public void callback(TaskContext taskctx) {
				System.out.println("final: " + taskctx.getResult());
				
				Logger.info("Task completed");
			}
		});
		
		ApplicationHub.stopServer();
	}
}
