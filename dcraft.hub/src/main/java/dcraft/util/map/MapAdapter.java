package dcraft.util.map;

import dcraft.hub.op.OperatingContextException;
import dcraft.log.Logger;
import dcraft.script.StackUtil;
import dcraft.script.work.ReturnOption;
import dcraft.script.work.StackWork;
import dcraft.struct.BaseStruct;
import dcraft.struct.RecordStruct;
import dcraft.struct.Struct;
import dcraft.struct.scalar.BooleanStruct;
import dcraft.struct.scalar.NullStruct;
import dcraft.struct.scalar.StringStruct;
import dcraft.util.StringUtil;
import dcraft.xml.XElement;

import java.io.IOException;

public class MapAdapter extends RecordStruct {
	@Override
	public ReturnOption operation(StackWork stack, XElement code) throws OperatingContextException {
		if ("Geocode".equals(code.getName())) {
			String address = Struct.objectToString(StackUtil.refFromElement(stack, code, "Address", true));
			String street = Struct.objectToString(StackUtil.refFromElement(stack, code, "Street", true));
			String city = Struct.objectToString(StackUtil.refFromElement(stack, code, "City", true));
			String state = Struct.objectToString(StackUtil.refFromElement(stack, code, "State", true));
			String zip = Struct.objectToString(StackUtil.refFromElement(stack, code, "Zip", true));
			String handle = Struct.objectToString(StackUtil.stringFromElement(stack, code, "Result"));

			String latlong = null;

			if (StringUtil.isNotEmpty(street) && StringUtil.isNotEmpty(city) && StringUtil.isNotEmpty(state) && StringUtil.isNotEmpty(zip))
				latlong = MapUtil.getLatLong(street, city, state, zip);
			else if (StringUtil.isNotEmpty(address))
				latlong = MapUtil.getLatLong(address);

			if (StringUtil.isNotEmpty(handle)) {
				if (StringUtil.isEmpty(latlong))
					StackUtil.addVariable(stack, handle, NullStruct.instance);
				else
					StackUtil.addVariable(stack, handle, StringStruct.of(latlong));
			}

			return ReturnOption.CONTINUE;
		}

		return super.operation(stack, code);
	}
}
