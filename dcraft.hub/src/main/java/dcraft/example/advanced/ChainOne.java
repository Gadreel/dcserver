package dcraft.example.advanced;

import dcraft.example.util.ExampleConfigLoader;
import dcraft.example.work.ChainInfoWork;
import dcraft.hub.app.ApplicationHub;
import dcraft.hub.op.OperationContext;
import dcraft.hub.op.UserContext;
import dcraft.log.Logger;
import dcraft.service.ServiceRequest;
import dcraft.task.*;

/*
 * test chain work
 */
public class ChainOne {
	public static void main(String... args) throws Exception {
		if (ApplicationHub.startServer(ExampleConfigLoader.local(ChainOne.class))) {
			// test work
			OperationContext.set(OperationContext.context(UserContext.rootUser()));
			
			IWork chain = ChainWork
					.of(ChainInfoWork.of("a", 1))
					.then(ChainInfoWork.of("b", 2))
					.then(ChainWork
							.of(ChainInfoWork.of("1", 3))
							.then(ChainInfoWork.of("2", 1))
							.then(ChainInfoWork.of("3aa", 2))
					)
					.then(ServiceRequest.of("ChainDataService", "Default", "Simple"))
					.then(ControlWork.dieOnError("Encountered service issue!"))
					.then(ChainInfoWork.of("y", 2))
					.then(taskctx ->  {
						Logger.info("Waiting on final chain");
						
						try {
							Thread.sleep(5000);
						}
						catch (InterruptedException x) {
							System.out.println("i");
						}
						
						Logger.info("Task 1 final chain");
						
						System.out.println("z from: " + taskctx.getParams());
						taskctx.returnValue("z");
					});
			
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
			
			Task task2 = Task.ofHubRoot()
					.withTitle("Second Chain Test")
					.withNextId("APP")
					.withWork(taskctx -> {
						Logger.info("Task 2 final chain");
						
						System.out.println("z from: " + taskctx.getParams());
						taskctx.returnValue("z");
					});
			
			TaskHub.submit(task2, new TaskObserver() {
				@Override
				public void callback(TaskContext taskctx) {
					System.out.println("final 2: " + taskctx.getResult());
					
					Logger.info("Task completed 2");
				}
			});
		}
		else {
			System.out.println("Application failed to start!");
		}
		
		ApplicationHub.stopServer();
	}
}
