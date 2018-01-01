package dcraft.hub.op;

import dcraft.struct.Struct;

public interface IVariableAware {
	Struct queryVariable(String name) throws OperatingContextException;
}
