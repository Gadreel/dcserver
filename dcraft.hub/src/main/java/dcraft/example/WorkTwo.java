package dcraft.example;

import java.util.Scanner;

import dcraft.example.work.SlowGreetWork;
import dcraft.struct.RecordStruct;
import dcraft.task.Task;
import dcraft.task.TaskHub;
import dcraft.task.run.WorkHub;
import dcraft.task.run.WorkTopic;
import dcraft.xml.XElement;

/*
 * Builds on WorkOne and shows how the Run Limit works.
 * 
 * RunLimits indicate the maximum number of 
 * Tasks that can run at the same time for a given
 * topic. Here we are set to a limit of 2 but will create
 * ten Greet tasks. Note, while running, that only two
 * greets are running at the same time.
 */
public class WorkTwo {
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

			String[] names = { "Harold", "Fran", "$tar", "Janet", "Dawn",
					"Barry", "Jack", "Cody", "Cindy", "Hilda" };
			
			for (String greet : names) {
				// create a task for the Conversation topic, pass a
				// parameter with the name to greet
				Task task = Task.ofHubRoot()
					.withTitle("Greetings for: " + greet)
					.withParams(new RecordStruct().with("Greet", greet))
					.withTopic("Conversation")
					.withWork(SlowGreetWork.class);
				
				// with Tasks you don't even necessarily care when it is done.
				// sometimes it is OK to start and forget
				TaskHub.submit(task);
			}
			
			System.out.println("All tasks submitted!");
			System.out.println("Wait for all tasks to complete then press enter to stop.");
			
			System.in.read();
		}
	}
}
