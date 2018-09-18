/* ************************************************************************
#
#  designCraft.io
#
#  http://designcraft.io/
#
#  Copyright:
#    Copyright 2014 eTimeline, LLC. All rights reserved.
#
#  License:
#    See the license.txt file in the project's top-level directory for details.
#
#  Authors:
#    * Andy White
#
************************************************************************ */
package dcraft.locale;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import dcraft.util.StringUtil;

/**
 * as much as possible dcServer attempts to avoid using Java's locales because they are
 * not extensible.  Translations rely entirely on our own Hub dictionaries.  Formatting
 * of dates and numbers still uses some Java locales for now.
 * 
 * @author Andy
 *
 */
public class LocaleUtil {
	/**
	 * Try to get a Java style Locale for a locale id string
	 * 
	 * @param ll locale id
	 * @return java type locale
	 */
	static public Locale getLocale(String ll) {		
		if (StringUtil.isEmpty(ll)) 
			return null;

		if (ll.contains("_")) {
			String[] lp = ll.split("_");
			return new Locale(lp[0], lp[1].toUpperCase());
		}
		
		return new Locale(ll);
	}

	static public List<IndexInfo> full(String v, String lang) {
		lang = LocaleUtil.normalizeCode(lang);

		// TODO configure
		if ("eng".equals(lang)) {
			IndexBase indexer = new dcraft.locale.index.eng.Index();

			return indexer.full(v);
		}

		if ("spa".equals(lang)) {
			IndexBase indexer = new dcraft.locale.index.spa.Index();

			return indexer.full(v);
		}

		return new ArrayList<>();
	}

	static public String fullSearch(String v, String lang) {
		return LocaleUtil.toSearch(LocaleUtil.full(v, lang));
	}

	static public String fullIndex(String v, String lang) {
		return LocaleUtil.toIndex(LocaleUtil.full(v, lang));
	}

	static public List<IndexInfo> simple(String v, String lang) {
		lang = LocaleUtil.normalizeCode(lang);

		// TODO configure
		if ("eng".equals(lang)) {
			IndexBase indexer = new dcraft.locale.index.eng.Index();

			return indexer.simple(v);
		}

		return new ArrayList<>();
	}

	static public String simpleSearch(String v, String lang) {
		return LocaleUtil.toSearch(LocaleUtil.simple(v, lang));
	}

	static public String simpleIndex(String v, String lang) {
		return LocaleUtil.toIndex(LocaleUtil.simple(v, lang));
	}

	static public String toSearch(List<IndexInfo> tokens) {
		StringBuilder sb = new StringBuilder("|");

		for (IndexInfo token : tokens) {
			sb.append(token.token);
			sb.append(';');
			sb.append(token.start);
			sb.append(';');
			sb.append(token.end);
			sb.append(';');
			sb.append(token.score);
			sb.append('|');
		}

		return sb.toString();
	}

	static public String toIndex(List<IndexInfo> tokens) {
		if (tokens == null)
			return null;
		
		StringBuilder sb = new StringBuilder();

		for (IndexInfo token : tokens) {
			if (sb.length() > 0)
				sb.append('_');

			String word = token.token;

			int strLen = word.length();
			int pos = 0;

			while ((pos != strLen) && (sb.length() < 64)) {
				char ch = word.charAt(pos);

				if (Character.isLetterOrDigit(ch))
					sb.append(ch);

				pos++;
			}

			if (sb.length() >= 64)
				break;
		}

		return sb.toString();
	}

	static public String normalizeCode(String code) {
		if (code == null)
			return null;

		// convert 2 char codes to 3 char codes
		if (code.length() == 2) {
			if ("en".equals(code))
				return "eng";

			if ("es".equals(code))
				return "spa";
		}

		return code;
	}
}
