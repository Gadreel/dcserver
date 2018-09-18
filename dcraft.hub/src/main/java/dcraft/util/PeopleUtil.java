package dcraft.util;

import dcraft.script.work.ReturnOption;

public class PeopleUtil {
	// currently only support USA numbers
	static public String formatPhone(String v) {
		if (StringUtil.isEmpty(v))
			return null;
		
		StringBuilder cleanto = new StringBuilder("+1");
		
		for (int i = 0 ; i < v.length(); i++) {
			if (Character.isDigit(v.charAt(i)))
				cleanto.append(v.charAt(i));
		}
		
		// currently only support USA numbers
		if (cleanto.length() != 12)
			return null;
		
		return cleanto.toString();
	}
}
