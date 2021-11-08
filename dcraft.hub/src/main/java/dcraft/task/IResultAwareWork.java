package dcraft.task;

import dcraft.hub.ResourceHub;
import dcraft.hub.op.OperatingContextException;
import dcraft.log.DebugLevel;
import dcraft.log.HubLog;
import dcraft.struct.BaseStruct;
import dcraft.struct.Struct;
import dcraft.util.StringUtil;

public interface IResultAwareWork extends IParentAwareWork {
	BaseStruct getResult() throws OperatingContextException;
	void setResult(BaseStruct v) throws OperatingContextException;
	
	void setExitCode(long code, String msg) throws OperatingContextException;
	void setExitCodeTr(long code, Object... params) throws OperatingContextException;
	void clearExitCode() throws OperatingContextException;
	boolean hasExitErrors() throws OperatingContextException;
	long getExitCode() throws OperatingContextException;
	String getExitMessage() throws OperatingContextException;
}
