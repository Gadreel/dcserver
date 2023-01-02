package dcraft.script.work;

import dcraft.hub.ResourceHub;
import dcraft.hub.op.IVariableAware;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
import dcraft.log.DebugLevel;
import dcraft.log.HubLog;
import dcraft.script.StackUtil;
import dcraft.script.inst.Instruction;
import dcraft.struct.BaseStruct;
import dcraft.struct.Struct;
import dcraft.struct.scalar.NullStruct;
import dcraft.task.IParentAwareWork;
import dcraft.task.IProgressAwareWork;
import dcraft.task.IResultAwareWork;
import dcraft.util.StringUtil;

public class CallFuncWork extends InstructionWork implements IResultAwareWork, IProgressAwareWork, IParentAwareWork, IVariableAware {
	static public CallFuncWork of(IParentAwareWork parent, Instruction inst) {
		CallFuncWork sw = new CallFuncWork();
		
		sw.parent = parent;
		sw.inst = inst;
		
		return sw;
	}
	
	protected InstructionWork currEntry = null;
	
	protected BaseStruct value = null;
	protected long exitcode = 0L;
	protected String exitmessage = null;
	
	// progress tracking
	protected int progTotalSteps = 0;
	protected int progCurrStep = 0;
	protected String progStepName = null;
	protected int progComplete = 0;
	protected String progMessage = null;
	
	protected CallFuncWork() {  }
	
	public InstructionWork getCurrEntry() {
		return this.currEntry;
	}
	
	public void setCurrEntry(InstructionWork v) {
		this.currEntry = v;
	}
	
	// TODO point to callable script - pass cancel to it
	
	// this is called from within the Script context, bypassing the std run ctx
	@Override
	public ReturnOption run() throws OperatingContextException {
		if (this.state == ExecuteState.READY) {
			String result = StackUtil.stringFromSource(this, "Result");

			if (StringUtil.isNotEmpty(result))
				StackUtil.addVariable(this.parent, result, NullStruct.instance);  // set default result
		}

		if ((inst == null) || (this.state == ExecuteState.DONE))
			return ReturnOption.DONE;
		
		// first call have inst set the current entry, future calls use that instead
		ReturnOption ret = (this.currEntry != null) ? this.currEntry.run() : this.inst.run(this);
		
		if (ret != ReturnOption.AWAIT)
			this.setState(ExecuteState.DONE);
		
		// that done applies to the called block, but not to the main program (calling block)
		if (ret == ReturnOption.DONE)
			return ReturnOption.CONTINUE;
		
		// applies to called block
		if ((ret == ReturnOption.CONTROL_BREAK) || (ret == ReturnOption.CONTROL_CONTINUE))
			return ReturnOption.CONTINUE;
		
		return ret;
	}
	
	@Override
	public BaseStruct queryVariable(String name) throws OperatingContextException {
		if ("_Arg".equals(name))
			return StackUtil.refFromSource(this, "Arg");
		
		IVariableAware va = StackUtil.queryVarAware(this.parent);

		if (va != null)
			return va.queryVariable(name);

		return OperationContext.getOrThrow().queryVariable(name);
	}

	@Override
	public BaseStruct getResult() {
		return this.value;
	}
	
	@Override
	public void setResult(BaseStruct v) throws OperatingContextException {
		this.value = v;

		String result = StackUtil.stringFromSource(this, "Result");

		if (StringUtil.isNotEmpty(result))
			StackUtil.addVariable(this.parent, result, v);
	}
	
	/**
	 * Overrides any previous return codes and messages
	 *
	 * @param code code for message
	 * @param msg message
	 */
	@Override
	public void setExitCode(long code, String msg) throws OperatingContextException {
		if (StringUtil.isNotEmpty(msg))
			OperationContext.getOrThrow().log(DebugLevel.Info, code, msg, "Exit");
		else if (this.getParent() == null)
			OperationContext.getOrThrow().boundary("Code", code + "", "Exit");
		
		this.exitcode = code;
		this.exitmessage = msg;
		
		if (StringUtil.isNotEmpty(msg))
			HubLog.log(OperationContext.getOrThrow().getController().getSeqNumber(), DebugLevel.Info, code, msg);
		else if (this.getParent() == null)
			HubLog.boundary(OperationContext.getOrThrow().getController().getSeqNumber(), 0, "Code", code + "", "Exit");
	}
	
	// let Logger translate to the language of the log file - let tasks translate to their own logs in
	// the language of the context
	
	@Override
	public void setExitCodeTr(long code, Object... params) throws OperatingContextException {
		String msg = OperationContext.getOrThrow().tr("_code_" + code, params);
		
		if (StringUtil.isNotEmpty(msg))
			OperationContext.getOrThrow().log(DebugLevel.Info, code, msg);
		else
			OperationContext.getOrThrow().boundary("Code", code + "", "Exit");
		
		this.exitcode = code;
		this.exitmessage = msg;
		
		String smsg = ResourceHub.trSys("_code_" + code, params);
		
		if (StringUtil.isNotEmpty(smsg))
			HubLog.log(OperationContext.getOrThrow().getController().getSeqNumber(), DebugLevel.Info, code, smsg);
		else
			HubLog.boundary(OperationContext.getOrThrow().getController().getSeqNumber(), 0, "Code", code + "", "Exit");
	}
	
	@Override
	public void clearExitCode() throws OperatingContextException {
		this.setExitCode(0, null);
	}
	
	@Override
	public boolean hasExitErrors() {
		return (this.exitcode != 0);
	}
	
	@Override
	public long getExitCode() {
		return this.exitcode;
	}
	
	@Override
	public String getExitMessage() throws OperatingContextException {
		return this.exitmessage;
	}
	
	/**
	 * @return units/percentage of task completed
	 */
	@Override
	public int getAmountCompleted() {
		return this.progComplete;
	}
	
	/**
	 * @param v units/percentage of task completed
	 */
	@Override
	public void setAmountCompleted(int v) {
		this.progComplete = v;
		
		// TODO review - this.controller.fireEvent(this, OperationConstants.PROGRESS, OperationConstants.PROGRESS_AMOUNT);
	}
	
	/**
	 * @return status message about task progress
	 */
	public String getProgressMessage() {
		return this.progMessage;
	}
	
	/**
	 * @param v status message about task progress
	 */
	@Override
	public void setProgressMessage(String v) {
		this.progMessage = v;
		
		// TODO review - this.controller.fireEvent(this, OperationConstants.PROGRESS, OperationConstants.PROGRESS_MESSAGE);
	}
	
	/**
	 * @param code message translation code
	 * @param params for the message string
	 */
	@Override
	public void setProgressMessageTr(int code, Object... params) throws OperatingContextException {
		this.progMessage = OperationContext.getOrThrow().tr("_code_" + code, params);
		
		// TODO review - this.controller.fireEvent(this, OperationConstants.PROGRESS, OperationConstants.PROGRESS_MESSAGE);
	}
	
	/**
	 * @return total steps for this specific task
	 */
	@Override
	public int getSteps() {
		return this.progTotalSteps;
	}
	
	/**
	 * @param v total steps for this specific task
	 */
	@Override
	public void setSteps(int v) {
		this.progTotalSteps = v;
	}
	
	/**
	 * @return current step within this specific task
	 */
	@Override
	public int getCurrentStep() {
		return this.progCurrStep;
	}
	
	/**
	 * Set step name first, this triggers observers
	 *
	 * @param step current step number within this specific task
	 * @param name current step name within this specific task
	 */
	@Override
	public void setCurrentStep(int step, String name) {
		this.progCurrStep = step;
		this.progStepName = name;
		
		// TODO review - this.controller.fireEvent(this, OperationConstants.PROGRESS, OperationConstants.PROGRESS_STEP);
	}
	
	/**
	 * Set step name first, this triggers observers
	 *
	 * @param name current step name within this specific task
	 */
	@Override
	public void nextStep(String name) {
		this.progCurrStep++;
		this.progStepName = name;
		
		// TODO review - this.controller.fireEvent(this, OperationConstants.PROGRESS, OperationConstants.PROGRESS_STEP);
	}
	
	/**
	 * @return name of current step
	 */
	@Override
	public String getCurrentStepName() {
		return this.progStepName;
	}
	
	/**
	 * @param step number of current step
	 * @param code message translation code
	 * @param params for the message string
	 */
	@Override
	public void setCurrentStepNameTr(int step, int code, Object... params) throws OperatingContextException {
		String name = OperationContext.getOrThrow().tr("_code_" + code, params);
		
		this.progCurrStep = step;
		this.progStepName = name;
		
		// TODO review - this.controller.fireEvent(this, OperationConstants.PROGRESS, OperationConstants.PROGRESS_STEP);
	}
	
	/**
	 * @param code message translation code
	 * @param params for the message string
	 */
	@Override
	public void nextStepTr(int code, Object... params) throws OperatingContextException {
		String name = OperationContext.getOrThrow().tr("_code_" + code, params);
		
		this.progCurrStep++;
		this.progStepName = name;
		
		// TODO review - this.controller.fireEvent(this, OperationConstants.PROGRESS, OperationConstants.PROGRESS_STEP);
	}
}
