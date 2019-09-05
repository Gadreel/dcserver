package dcraft.struct.format;

import dcraft.hub.ResourceHub;
import dcraft.struct.ListStruct;
import dcraft.struct.Struct;
import dcraft.util.StringUtil;

public class EnumFormatter implements IFormatter {
	@Override
	public FormatResult format(Object value, String op, String format) {
		if (value instanceof ListStruct) {
			ListStruct list = (ListStruct) value;
			ListStruct resp = ListStruct.list();
			
			for (int i = 0; i <list.size(); i++) {
				String val = list.getItemAsString(i);
				
				if ((val == null) || StringUtil.isEmpty(format)) {
					resp.with(null);
				}
				else {
					resp.with(ResourceHub.getResources().getLocale().tr("_enum_" + format + "_" + val));
				}
			}
			
			return FormatResult.result(resp);
		}
		
		String val = Struct.objectToString(value);
		
		if ((val == null) || StringUtil.isEmpty(format)) {
			value = null;
		}
		else {
			value = ResourceHub.getResources().getLocale().tr("_enum_" + format + "_" + val);
		}
		
		return FormatResult.result(value);
	}
}
