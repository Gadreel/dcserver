package dcraft.example.advanced;

import dcraft.example.util.ExampleConfigLoader;
import dcraft.example.util.ExampleUtil;
import dcraft.example.work.GreetListWork;
import dcraft.example.work.SlowGreetWork;
import dcraft.hub.ResourceHub;
import dcraft.hub.app.ApplicationHub;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
import dcraft.hub.op.UserContext;
import dcraft.log.DebugLevel;
import dcraft.log.Logger;
import dcraft.struct.ListStruct;
import dcraft.struct.RecordStruct;
import dcraft.struct.Struct;
import dcraft.task.*;
import dcraft.task.run.WorkHub;
import dcraft.task.run.WorkTopic;
import dcraft.xml.XElement;

import java.util.Scanner;

/*
 * Start some work but then stop server immediately - see how server handles Task stopping
 */
public class ShutdownOne {
	public static void main(String... args) throws Exception {
		ApplicationHub.startServer(ExampleConfigLoader.local(ShutdownOne.class));
		
		WorkHub.addTopic(WorkTopic.of("Conversation", 2));
			
		/*
		 * We'll automate the list of names for greeting so that
		 * you can concentrate on how the Run Limit is working.
		 */
		
		OperationContext.set(OperationContext.context(UserContext.rootUser()));
	
		ListStruct names = ListStruct.list( "Harold", "Fran", "$tar", "Janet", "Dawn",
				"Barry", "Jack", "Cody", "Cindy", "Hilda");
		
		for (Struct ns : names.items()) {
			// create a subtask from this task.
			Task task = Task.ofSubtask("Greetings for: " + ns.toString(), "Greet")
					.withParams(new RecordStruct().with("Greet", ns.toString()))
					.withTopic("Conversation")
					.withWork(SlowGreetWork.class);
			
			TaskHub.submit(task);
		}
		
		/*
		// create a task for the Conversation topic, pass a
		// parameter with the name to greet
		Task task = Task.ofHubRoot()
				.withTitle("Greetings List")
				.withParams(new RecordStruct().with("Names", names))
				.withTopic("Conversation")
				.withWork(GreetListWork.class);
		
		TaskLogger logger = new TaskLogger();
		
		// note you may pass more than one observer (a logger is an observer) during submit
		TaskHub.submit(task, logger, new TaskObserver() {
			@Override
			public void callback(TaskContext task) {
				System.out.println("Greetings listing is complete. Task has errors: " + task.hasErrors());
				System.out.println("Controller has errors: " + task.getController().hasLevel(0,-1, DebugLevel.Error));
				System.out.println();
				
				System.out.println("Here is the output from the task logger:");
				System.out.print(logger.logToString());
			}
		});
		*/
		
		ApplicationHub.stopServer();
	}
}
