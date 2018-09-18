package dcraft.struct.format;

import dcraft.hub.op.OperatingContextException;

public interface IFormatter {
	FormatResult format(Object value, String op, String format) throws OperatingContextException;
}
