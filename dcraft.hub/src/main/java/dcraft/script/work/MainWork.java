package dcraft.script.work;

import dcraft.hub.ResourceHub;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
import dcraft.log.DebugLevel;
import dcraft.log.HubLog;
import dcraft.log.Logger;
import dcraft.script.StackUtil;
import dcraft.script.inst.Instruction;
import dcraft.session.Session;
import dcraft.session.SessionHub;
import dcraft.struct.CompositeStruct;
import dcraft.struct.Struct;
import dcraft.struct.scalar.NullStruct;
import dcraft.task.*;
import dcraft.util.StringUtil;

public class MainWork extends BlockWork implements IResultAwareWork, IChainAwareWork {
	static public MainWork of(IParentAwareWork parent, Instruction inst) {
		MainWork sw = new MainWork();
		
		sw.parent = parent;
		sw.inst = inst;
		
		return sw;
	}
	
	protected Struct value = null;
	protected long exitcode = 0L;
	protected String exitmessage = null;
	
	protected MainWork() { }
	
	@Override
	public void run(TaskContext taskctx) throws OperatingContextException {
		if (this.getState() == ExecuteState.READY) {
			Struct param = taskctx.getParams();
			
			if (param == null)
				param = NullStruct.instance;
			
			this.addVariable("_Param", param);
			
			String pname = StackUtil.stringFromSource(this, "Param");
			
			if (StringUtil.isNotEmpty(pname))
				this.addVariable(pname, param);

			// scripts should have a Session
			if (taskctx.getSessionId() == null) {
				Session sess = Session.of("script:", taskctx.getUserContext());

				//if (Logger.isDebug())
				//	Logger.debug("Script session request f3");

				SessionHub.register(sess);

				taskctx.setSessionId(sess.getId());
			}
		}
		
		if (this.run() != ReturnOption.AWAIT) {
			if (this.getState() == ExecuteState.DONE)
				this.propogateResults();
			
			taskctx.returnResult();
		}
	}
	
	protected void propogateResults() throws OperatingContextException {
		IResultAwareWork resultAwareWork = StackUtil.queryResultAware(this.parent);
		
		if (resultAwareWork != null) {
			resultAwareWork.setResult(this.value);
			resultAwareWork.setExitCode(this.exitcode, this.exitmessage);
		}
		else {
			TaskContext ctx = OperationContext.getAsTaskOrThrow();
			ctx.setResult(this.value);
			ctx.setExitCode(this.exitcode, this.exitmessage);
		}
	}
	
	@Override
	public void resumeNext(TaskContext taskctx) throws OperatingContextException {
		this.run(taskctx);	// TODO this may not be ideal, does it mess with debugging the script?
	}
	
	@Override
	public boolean isComplete(TaskContext taskctx) {
		return (this.getState() == ExecuteState.DONE);
	}
	
	@Override
	public Struct getResult() {
		return this.value;
	}
	
	@Override
	public void setResult(Struct v) {
		this.value = v;
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
		else
			OperationContext.getOrThrow().boundary("Code", code + "", "Exit");
		
		this.exitcode = code;
		this.exitmessage = msg;
		
		if (StringUtil.isNotEmpty(msg))
			HubLog.log(OperationContext.getOrThrow().getController().getSeqNumber(), DebugLevel.Info, code, msg, "Exit");
		else
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
}
