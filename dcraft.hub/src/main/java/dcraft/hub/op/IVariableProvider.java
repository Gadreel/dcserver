package dcraft.hub.op;

import dcraft.struct.BaseStruct;
import dcraft.struct.RecordStruct;
import dcraft.struct.Struct;

public interface IVariableProvider extends IVariableAware {
	RecordStruct variables() throws OperatingContextException;
	void addVariable(String name, BaseStruct var) throws OperatingContextException;
	void clearVariables() throws OperatingContextException;
}
