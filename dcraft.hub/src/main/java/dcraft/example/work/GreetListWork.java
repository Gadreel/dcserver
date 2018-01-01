package dcraft.example.work;

import dcraft.hub.op.OperatingContextException;
import dcraft.log.Logger;
import dcraft.struct.ListStruct;
import dcraft.struct.RecordStruct;
import dcraft.task.IWork;
import dcraft.task.Task;
import dcraft.task.TaskContext;
import dcraft.task.TaskHub;
import dcraft.task.TaskObserver;

/*
 * There are two important concepts in this Work. First is 
 * the use of subtasks which is documented below. The other concept
 * is that Work can be resumed.
 * 
 * What does it mean to resume work? It means that "void run(TaskContext ctx)"
 * will be run again on the work but without the overhead that recursion
 * creates and with the possibility of some threading control that is an
 * advanced topic.
 * 
 * Each time a greet task completes we want this Work to run again and collect
 * the next name from the list. When we run out of names we'll end.
 */
public class GreetListWork implements IWork {
	// where in the list to find the next name to greet
	// start at the first
	protected int current = 0;
	
	@Override
	public void run(TaskContext ctx) throws OperatingContextException {
		// get the parameters from caller
		RecordStruct params = ctx.getTask().getParamsAsRecord();
		
		if (params == null) {
			Logger.error("Unable to Greet, missing params structure.");
			ctx.setExitCode(100, "Missing params");
			ctx.returnEmpty();
			return;
		}
		
		ListStruct names = params.getFieldAsList("Names");
		
		if (names == null) {
			Logger.error("Unable to Greet, missing Greet param.");
			ctx.setExitCode(101, "Missing Greet param");
			ctx.returnEmpty();
			return;
		}
		
		// at this point we have the list of names
		// recall that this code is run once for every name on the list so 
		// current could be any number here - check that it is not greater than
		// the end of the list - if it is then we are done
		if (this.current >= names.size()) {
			Logger.info("Greet list is completed.");
			ctx.returnEmpty();
			return;
		}
		
		// otherwise get the current name from the list
		String greet = names.getItemAsString(this.current);
		
		Logger.info("Running greet task for: " + greet);
		
		// and increment current so next call (the resume) will get the next name
		this.current++;
		
		// create a subtask from this task.
		Task task = Task.ofSubtask(ctx, "Greetings for: " + greet, "Greet")
			.withParams(new RecordStruct().with("Greet", greet))
			.withWork(SlowGreetWork.class);

		// when the greet is complete call "run" method (this method) again using 
		// the "resume" technique
		// this will run a greet for the next name on the list (because current is 
		// incremented. This resume idea might be the most challenging idea presented
		// so far - so take a close look at the code and maybe review traditional
		// recursion. This code achieves a similar result but is coded differently
		// from traditional recursion.
		TaskHub.submit(task, new TaskObserver() {
			@Override
			public void callback(TaskContext subtask) {
				Logger.info("Subtask had error: " + subtask.hasExitErrors());
				ctx.resume();
			}
		});
	}
}
