package dcraft.example;

import java.util.Scanner;
import java.util.concurrent.CountDownLatch;
import java.util.function.Consumer;

import dcraft.example.advanced.ChainOne;
import dcraft.example.util.ExampleConfigLoader;
import dcraft.example.util.ExampleUtil;
import dcraft.hub.ResourceHub;
import dcraft.hub.app.ApplicationHub;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
import dcraft.hub.op.OperationOutcomeEmpty;
import dcraft.hub.op.UserContext;
import dcraft.task.ISchedule;
import dcraft.task.IWork;
import dcraft.task.Task;
import dcraft.task.TaskContext;
import dcraft.task.TaskHub;
import dcraft.task.run.WorkHub;
import dcraft.task.run.WorkTopic;
import dcraft.task.scheduler.ScheduleHub;
import dcraft.xml.XElement;

/*
 * Here we learn one way to schedule future work - both to run once
 * and to run repeatedly.
 */
public class WorkSix {
	public static void main(String... args) throws Exception {
		if (ApplicationHub.startServer(ExampleConfigLoader.local(WorkSix.class))) {
			// Scanner let's use read user input
			try (Scanner scan = new Scanner(System.in, "UTF-8")) {
				/*
				 * create a simple task to greet Harold
				 */
				XElement config1 = ResourceHub.getResources().getConfig().getTag("TaskOne");
				int secs1 = 3;
				
				if (config1 != null)
					secs1 = (int) config1.getAttributeAsInteger("Seconds", secs1);
				
				Task task1 = Task.ofHubRoot()
						.withTitle("Single Greeting")
						.withWork(new IWork() {
							@Override
							public void run(TaskContext taskctx) throws OperatingContextException {
								System.out.println("Hello Harold!");
								taskctx.returnEmpty();
							}
						});
				
				// run task 1 in three seconds (or what ever is in config)
				TaskHub.scheduleIn(task1, secs1);
				
				System.out.println("Wait for first schedule to run, then press enter to continue.");
				
				System.in.read();        // after task one
				
				/*
				 * create a simple task to repeatedly greet Fran
				 */
				XElement config2 = ResourceHub.getResources().getConfig().getTag("TaskTwo");
				int secs2 = 2;
				
				if (config2 != null)
					secs2 = (int) config2.getAttributeAsInteger("Seconds", secs2);
				
				Task task2 = Task.ofHubRoot()
						.withTitle("Repeating Greeting")
						.withWork(new IWork() {
							@Override
							public void run(TaskContext taskctx) throws OperatingContextException {
								System.out.println("Hello Fran!");
								taskctx.returnEmpty();
							}
						});
				
				// run task 2 once every 2 seconds (or what ever is in config)
				ISchedule sched2 = TaskHub.scheduleEvery(task2, secs2);
				
				System.out.println("Wait for second schedule to run a few times then press enter.");
				
				System.in.read();
				
				/*
				 * stop schedule 2 from running any more
				 */
				sched2.cancel();
				
				System.out.println("Second schedule halted, press enter to stop.");
				
				System.in.read();
			}
		}
		
		ApplicationHub.stopServer();
	}
}
