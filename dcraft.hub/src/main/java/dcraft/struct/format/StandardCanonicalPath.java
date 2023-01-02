package dcraft.struct.format;

import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
import dcraft.struct.BaseStruct;
import dcraft.struct.ListStruct;

public class StandardCanonicalPath implements IFormatter {
	@Override
	public FormatResult format(Object value, String op, String format) throws OperatingContextException {
		if (value instanceof ListStruct) {
			ListStruct list = (ListStruct) value;
			String resp = "";

			for (int i = 0; i < list.size(); i++) {
				resp += "/" + list.getItemAsString(i);
			}

			// TODO support the other dynamic extensions (md, php, xml)
			if (resp.endsWith(".html"))
				resp = resp.substring(0, resp.length() - 5);

			if (resp.equals(OperationContext.getOrThrow().getSite().getHomePath().toString()))
				resp = "/";

			return FormatResult.result(resp);
		}

		return FormatResult.result(value);
	}
}
