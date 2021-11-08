package dcraft.hub.op;

import dcraft.struct.BaseStruct;
import dcraft.struct.Struct;

public interface IVariableAware {
	BaseStruct queryVariable(String name) throws OperatingContextException;
}
