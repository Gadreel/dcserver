package dcraft.task;

public interface IProgressMonitorWork extends IParentAwareWork {
	int getAmountCompleted();
	
	/**
	 * @return status message about task progress
	 */
	String getProgressMessage();
	
	/**
	 * @return total steps for this specific task
	 */
	int getSteps();
	
	/**
	 * @return current step within this specific task
	 */
	int getCurrentStep();
	
	/**
	 * @return name of current step
	 */
	String getCurrentStepName();
}
