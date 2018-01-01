package dcraft.task;

import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationConstants;

public interface IProgressAwareWork extends IProgressMonitorWork {
	/**
	 * @param v units/percentage of task completed
	 */
	void setAmountCompleted(int v) throws OperatingContextException;
	
	/**
	 * @param v status message about task progress
	 */
	void setProgressMessage(String v) throws OperatingContextException;
	
	/**
	 * @param code message translation code
	 * @param params for the message string
	 */
	void setProgressMessageTr(int code, Object... params) throws OperatingContextException;
	
	/**
	 * @param v total steps for this specific task
	 */
	void setSteps(int v) throws OperatingContextException;
	
	/**
	 * Set step name first, this triggers observers
	 *
	 * @param step current step number within this specific task
	 * @param name current step name within this specific task
	 */
	void setCurrentStep(int step, String name) throws OperatingContextException;
	
	/**
	 * Set step name first, this triggers observers
	 *
	 * @param name current step name within this specific task
	 */
	void nextStep(String name) throws OperatingContextException;
	
	/**
	 * @param step number of current step
	 * @param code message translation code
	 * @param params for the message string
	 */
	void setCurrentStepNameTr(int step, int code, Object... params) throws OperatingContextException;
	
	/**
	 * @param code message translation code
	 * @param params for the message string
	 */
	void nextStepTr(int code, Object... params) throws OperatingContextException;
}
