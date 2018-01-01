package dcraft.example.work;

import java.nio.file.Path;

import dcraft.hub.op.IOperationLogger;
import dcraft.hub.op.OperatingContextException;
import dcraft.log.Logger;
import dcraft.struct.ListStruct;
import dcraft.struct.RecordStruct;
import dcraft.task.StateWork;
import dcraft.task.StateWorkStep;
import dcraft.task.Task;
import dcraft.task.TaskContext;
import dcraft.task.TaskHub;
import dcraft.task.TaskObserver;
import dcraft.util.FileUtil;
import dcraft.util.IOUtil;

/*
 * There are two important concepts in this Work. First is 
 * the use of subtasks which is documented below. The other concept
 * is that Work can be resumed.
 * 
 * Functionally this class isn't much different from GreetListWork,
 * except that it saves the log of all the greetings calls in step three.
 * However, structurally we are different from GreetListWork because we
 * have three separate steps.
 * 
 * 1. in the initialization step we check that the task parameters are correct.
 * This step runs only once, unlike GreetListWork which checks the parameters
 * for each greeting.
 * 
 * 2. now the greeting is done, this step is repeated once for each name in the 
 * list.
 * 
 * 3. in the final step the work log is save to a file
 * 
 */
public class GreetListLogWork extends StateWork {
	// where in the list to find the next name to greet
	// start at the first
	protected int current = 0;
	protected ListStruct names = null;

	/*
	 * In prep list all steps, in the order they will run
	 */
	@Override
	public void prepSteps(TaskContext trun) throws OperatingContextException {
		this
			.withStep(StateWorkStep.of("Initialize", this::initialize))
			.withStep(StateWorkStep.of("Process Greetings", this::greet))
			.withStep(StateWorkStep.of("Save log file", this::save));
	}
	
	/*
	 * STEP 1: check that the parameters are valid - STOP (work) if not, NEXT (step) if yes
	 */
	public StateWorkStep initialize(TaskContext ctx) throws OperatingContextException {
		// get the parameters from caller
		RecordStruct params = ctx.getTask().getParamsAsRecord();
		
		Logger.info("Initialize once");
		
		if (params == null) {
			Logger.error(100, "Unable to Greet, missing params structure.");
			return StateWorkStep.STOP;
		}
		
		this.names = params.getFieldAsList("Names");
		
		if (this.names == null) {
			Logger.error(101, "Unable to Greet, missing Greet param.");
			return StateWorkStep.STOP;
		}
		
		return StateWorkStep.NEXT;
	}
	
	/*
	 * STEP 2: run once for each name in list, resume the this task when
	 * subtask is complete
	 */
	public StateWorkStep greet(TaskContext ctx) throws OperatingContextException {
		// at this point we have the list of names
		// recall that this code is run once for every name on the list so 
		// current could be any number here - check that it is not greater than
		// the end of the list - if it is then we are done
		if (this.current >= this.names.size()) {
			Logger.info("Greet list is completed.");
			return StateWorkStep.NEXT;
		}
		
		// otherwise get the current name from the list
		String greet = this.names.getItemAsString(this.current);
		
		Logger.info("Running greet task for: " + greet);
		
		// and increment current so next call (the resume) will get the next name
		this.current++;
		
		// create a subtask from this task.
		Task task = Task.ofSubtask(ctx, "Greetings for: " + greet, "Greet")
			.withParams(new RecordStruct().with("Greet", greet))
			.withWork(SlowGreetWork.class);

		// when the greet subtask is complete run this step again using the "resume" technique
		// this will run a greet for the next name on the list (because current is 
		// incremented. This code achieves a similar result but is coded differently
		// from traditional recursion.
		TaskHub.submit(task, new TaskObserver() {
			@Override
			public void callback(TaskContext subtask) {
				ctx.resume();
			}
		});
		
		return StateWorkStep.WAIT;		// don't go to the next step, wait until we call "resume"
	}
	
	public StateWorkStep save(TaskContext ctx) throws OperatingContextException {
		IOperationLogger logger = ctx.getController().getLogger();
		
		if (logger == null) {
			Logger.info("No task logger found, will not save log file.");
		}
		else {
			Path tempfile = FileUtil.allocateTempFile("log");
			
			Logger.info("Saving the task log to: " + tempfile);
			
			IOUtil.saveEntireFile(tempfile, logger.logToString());
		}
		
		return StateWorkStep.NEXT;		// here NEXT will end the work because we are the last step
	}
}
