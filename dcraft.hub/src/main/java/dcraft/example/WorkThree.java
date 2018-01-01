package dcraft.example;

import java.util.Scanner;

import dcraft.example.work.GreetListWork;
import dcraft.struct.ListStruct;
import dcraft.struct.RecordStruct;
import dcraft.task.Task;
import dcraft.task.TaskContext;
import dcraft.task.TaskHub;
import dcraft.task.TaskLogger;
import dcraft.task.TaskObserver;
import dcraft.task.run.WorkHub;
import dcraft.task.run.WorkTopic;
import dcraft.xml.XElement;

/*
 * Builds on WorkTwo and shows how subtasks and logging work.
 * 
 * Subtasks are task that are submitted from within a task. 
 * 
 * Logging (see CallFour for a refresher) tasks is much like logging
 * operations. If a task has a logger enabled then the logger will
 * also collect all log messages from the subtasks as well.
 * 
 * We will demonstrate both concepts here, a task with subtasks and
 * logging. 
 */
public class WorkThree {
	public static void main(String... args) throws Exception {
		// Scanner let's use read user input
		try (Scanner scan = new Scanner(System.in, "UTF-8")) {
			/*
			 * Below is the configuration to limit to
			 * 2 tasks at once.
			 * 
				<WorkHub>
					<Topic Name="Conversation" RunLimit="2" />
				</WorkHub>
			 * 
			 * So initialize the WorkHub.
			 */
			
			WorkHub.minStart();
			
			WorkHub.addTopic(WorkTopic.of("Conversation", 2));
			
			/*
			 * We'll automate the list of names for greeting so that
			 * you can concentrate on how the Run Limit is working.
			 */

			ListStruct names = ListStruct.list( "Harold", "Fran", "$tar", "Janet", "Dawn",
					"Barry", "Jack", "Cody", "Cindy", "Hilda");
			
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
					System.out.println("Greetings listing is complete.");
					System.out.println();
					
					System.out.println("Here is the output from the task logger:");
					System.out.print(logger.logToString());
				}
			});
			
			System.out.println("All tasks submitted!");
			System.out.println("Wait for all tasks to complete then press enter to stop.");
			
			System.in.read();
		}
	}
}
