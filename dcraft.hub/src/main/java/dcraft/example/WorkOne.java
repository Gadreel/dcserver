package dcraft.example;

import java.util.Scanner;

import dcraft.example.work.SlowGreetWork;
import dcraft.struct.RecordStruct;
import dcraft.task.Task;
import dcraft.task.TaskContext;
import dcraft.task.TaskHub;
import dcraft.task.TaskObserver;
import dcraft.task.run.WorkHub;
import dcraft.task.run.WorkTopic;
import dcraft.xml.XElement;

/*
 * The concept of Work in dcServer is a little different from functions
 * and services. Functions and Services exist generally as a way to
 * collect data for the caller, or to update (add/remove) data for the
 * caller. Work on the other hand is intended to perform data updates
 * and then typically return only success or failure. It isn't a matter
 * so much of the caller getting or updating data as the caller is just
 * telling the system to "do this work now".
 * 
 * A classic example is Batch data process - once every night a server 
 * might scan through records and send email notices to certain users.
 * If a user hasn't logged in for 6 months - send an email saying you
 * miss them.
 * 
 * Although Work is more like a Batch system than a Service or Function
 * it can used in a way that is familiar to Function (OperationOutcomes).
 * 
 * Tasks are ways to define how Work will run in dcServer.
 * Our first example creates a Task that will take a name as an input and 
 * print a greeting. Very simple - not useful - work but it shows the parts
 * of Work.
 * 
 */
public class WorkOne {
	public static void main(String... args) throws Exception {
		// Scanner let's use read user input
		try (Scanner scan = new Scanner(System.in, "UTF-8")) {
			/*
			 * WorkHub helps run Tasks. There is also TaskHub.
			 * TaskHub is what you'll primarily use when coding
			 * dcServer - with TaskHub you can schedule work.
			 * 
			 * But we want to demonstrate how run limits work
			 * here so we also need to initialize WorkHub
			 * in this example.
			 * 
			 * RunLimits indicate the maximum number of 
			 * Tasks that can run at the same time for a given
			 * topic. Below is the configuration to limit to
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
			 * Now any task that is for the Conversation topic can only
			 * run if there is less than 2 already running. Otherwise it
			 * is placed in a queue and will run as soon as there is
			 * a slot in the topic for it to run.
			 * 
			 * We'll prompt the user for names to greet.
			 */

			System.out.println("Who to greet? (0 = quit)");
			
			while (true) {
				// read user input
				String greet = scan.nextLine();
				
				if ("0".equals(greet)) 
					break;
				
				// create a task for the Conversation topic, pass a
				// parameter with the name to greet
				Task task = Task.ofHubRoot()
					.withTitle("Greetings for: " + greet)
					.withParams(
						RecordStruct.record()
							.with("Greet", greet)
					)
					.withTopic("Conversation")
					.withWork(SlowGreetWork.class);
				
				// with tasks we don't have Outcomes, instead we have Observers. Yet there
				// are some similarities - your operation context (if you had one - this 
				// example does not) will still be intact as with Outcomes. You can check
				// for errors in the result - but note that the result is the TaskContext
				// not some data structure as will Outcomes. 
				//
				// You might recall Observers from CallFour's example of a logger 
				// (OperationObserver)
				TaskHub.submit(task, new TaskObserver() {
					@Override
					public void callback(TaskContext subtask) {
						// check for errors with the task
						if (subtask.hasExitErrors())
							// note that tasks may have exit codes and exit messages
							System.out.println("Encountered errors since last submit! Code: " +
									subtask.getExitCode() + " - Message: " + subtask.getExitMessage());
						
						// prompt for new greeting
						System.out.println("Who to greet next?");
					}
				});
			}
		}
	}
}
