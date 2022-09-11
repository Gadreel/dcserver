package dcraft.struct.format;

import dcraft.struct.Struct;
import dcraft.util.StringUtil;

// if string starts with a number, pad it out with zeros
public class YouTubeId implements IFormatter {
	@Override
	public FormatResult format(Object value, String op, String format) {
		// TODO act on a list as well as on a scalar

		String val = Struct.objectToString(value);

		if (StringUtil.isNotEmpty(val)) {
			value = YouTubeId.getIdFromUrl(val);
		}

		return FormatResult.result(value);
	}

	static public String getIdFromUrl(String url) {
		/*
		https://www.youtube.com/watch?v=xTY0SlyVfCQ
		https://youtu.be/xTY0SlyVfCQ
		https://youtu.be/xTY0SlyVfCQ?t=600
		https://www.youtube.com/watch?v=Qi-vLqHsEro
		https://www.youtube.com/watch?v=xQFT67-Q9sg
		 */

		if (StringUtil.isEmpty(url))
			return url;

		if (url.startsWith("https://www.youtube.com/watch?")) {
			url = url.substring(30);

			// strip #
			int pos = url.indexOf('#');

			if (pos > -1)
				url = url.substring(0, pos);

			// break up params
			String[] parts = url.split("&");

			for (int i = 0; i < parts.length; i++) {
				if (parts[i].startsWith("v=")) {
					url = parts[i].substring(2);
					break;
				}
			}
		}
		else if (url.startsWith("https://youtu.be/")) {
			url = url.substring(16);
			int pos = url.indexOf('?');

			if (pos > -1)
				url = url.substring(0, pos);
		}

		return url;
	}
}
