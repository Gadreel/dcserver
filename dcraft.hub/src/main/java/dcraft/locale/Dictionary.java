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

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import dcraft.log.Logger;
import dcraft.util.StringUtil;
import dcraft.xml.XElement;
import dcraft.xml.XmlReader;

// TODO code so that we work with Alpha 2, 3 or 4 codes automatically
public class Dictionary {
	static public Dictionary create() {
		return new Dictionary();
	}
	
	protected Map<String,Translation> translations = new HashMap<>();

	// caching
	//protected Map<String,TranslationChain> translationscache = new HashMap<String,TranslationChain>();
	
	public Collection<Translation> getTranslations() {
		return this.translations.values();
	}
	
	protected Dictionary() {
	}
	
	public String findToken(String locale, String token) {
		Translation tr = this.translations.get(locale);
		
		if (tr != null)
			return tr.get(token);
		
		return null;
	}
		
	public void load(Path fl) {
		if (fl == null) {
			// do not use Tr because dictionary may not be loaded
			Logger.error("Unable to apply dictionary file, file null", "Code", "106");
			return;
		}
		
		if (!Files.exists(fl)) {
			// do not use Tr because dictionary may not be loaded
			Logger.error("Missing dictionary file, expected: " + fl.normalize(), "Code", "107");
			return;
		}
		
		this.load(XmlReader.loadFile(fl, false, true));
	}
	
	protected Translation getOrAddLocale(String name) {
		Translation t = this.translations.get(name);
		
		if (t == null) {
			t = new Translation(name);
			this.translations.put(name, t);
		}
		
		return t;
	}
	
	public void load(XElement trroot) {
		if (trroot == null) {
			// do not use Tr because dictionary may not be loaded
			Logger.error("Unable to apply dictionary file, missing xml", "Code", "105");
			return;
		}
		
		for (XElement lel : trroot.selectAll("Locale")) {
			String lname = lel.getAttribute("Id");
			
			if (StringUtil.isEmpty(lname))
				continue;
			
			Translation t = this.getOrAddLocale(lname);
			
			for (XElement tel : lel.selectAll("Entry")) {
				String tname = tel.getAttribute("Token");
				
				if (StringUtil.isEmpty(tname))
					continue;
				
				String v = tel.getAttribute("Value");
				
				if (StringUtil.isEmpty(v))
					v = tel.getText();
				
				if (StringUtil.isEmpty(v))
					continue;
				
				t.put(tname, v);
			}
		}
	}
}
