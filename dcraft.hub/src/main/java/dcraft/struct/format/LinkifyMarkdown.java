package dcraft.struct.format;

import dcraft.struct.BaseStruct;
import dcraft.struct.ListStruct;
import dcraft.struct.Struct;
import dcraft.struct.scalar.StringStruct;
import dcraft.util.StringUtil;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LinkifyMarkdown implements IFormatter {
	// only trim strings, ignore all other types

	@Override
	public FormatResult format(Object value, String op, String format) {
		// TODO list option untested
		if (value instanceof ListStruct) {
			ListStruct list = (ListStruct) value;

			for (int i = 0; i <list.size(); i++) {
				BaseStruct val = list.getItem(i);

				if ((val != null) && ! val.isEmpty() && (val instanceof StringStruct)) {
					((StringStruct) val).adaptValue(LinkifyMarkdown.format(val.toString().trim()));
				}
			}

			return FormatResult.result(list);
		}

		String val = Struct.objectToString(value);

		if (StringUtil.isNotEmpty(val)) {
			value = LinkifyMarkdown.format(val);
		}

		return FormatResult.result(value);
	}

	static public String format(String message) {
		String result = message;

		Pattern urlPattern = Pattern.compile("(^|\\s)http(s)?:\\/\\/\\S+",Pattern.CASE_INSENSITIVE | Pattern.MULTILINE);

		Matcher m = urlPattern.matcher(message);

		while (m.find()) {
			System.out.println("------");

			System.out.println("> " + m.group(0));

			for (int i = 0; i < m.groupCount(); i++) {
				System.out.println(i + ") " + m.group(i));
			}

			String url = m.group(0).trim();

			// TODO broken if the same url appears multiple times, better to build up a string using the matched positions...future

			// strip out . and , and such on the end
			for (int i = url.length(); i > 0; i--) {
				char n = url.charAt(i - 1);

				if (Character.isLetterOrDigit(n) || (n == '/')) {
					url = url.substring(0, i);
					break;
				}
			}

			result = result.replace(url, "[" + url + "](" + url + ")");
		}

		return result;
	}
}
