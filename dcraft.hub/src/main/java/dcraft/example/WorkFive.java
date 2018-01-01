package dcraft.example;

import java.util.Scanner;

import dcraft.example.work.ChattyListLogWork;
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
 * Builds on WorkFour and shows using results of work plus another subtask.
 * 
 * The list of names and the greetings are the same as the last example,
 * what has changed is: 
 * 
 * 1. the parent work is using ChattyGreetWork instead of SlowGreetWork.
 * ChattyGreetWork returns a random number 1 to 10, our next call
 * to ChattyGreetWork (next name on list) we pass the number from the last
 * call to tell this new name how many times to print - the point being
 * to see how results from one work can then be used as inputs for other
 * work.
 * 
 * 2. the log file is compressed before being saved to disk, the compression
 * is done in a subtask showing another example of subtasks in a parent task.
 * 
 * The best place to review the changes is in the class ChattyListLogWork.
 */
public class WorkFive {
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
				.withWork(ChattyListLogWork.class);
			
			TaskLogger logger = new TaskLogger();
			
			// note you may pass more than one observer (a logger is an observer) during submit
			TaskHub.submit(task, logger, new TaskObserver() {
				@Override
				public void callback(TaskContext task) {
					System.out.println("Greetings listing is complete.");
					System.out.println();
				}
			});
			
			System.out.println("All tasks submitted!");
			System.out.println("Wait for all tasks to complete then press enter to stop.");
			
			System.in.read();
		}
	}
}
