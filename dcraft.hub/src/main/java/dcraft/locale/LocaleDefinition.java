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

import java.util.Locale;

import dcraft.util.StringUtil;

public class LocaleDefinition {
	// assumes name is [lang]/[variant] - this is because some languages are x-name/x-variant
	static public LocaleDefinition fromName(String name) {
		LocaleDefinition ld = new LocaleDefinition();
		
		ld.name = name;
		ld.loc = LocaleUtil.getLocale(name);		// TODO convert to ln-LL ??? 
		
		int pos = ld.name.indexOf('_');
		
		ld.lang = (pos != -1) ? ld.name.substring(0, pos) : ld.name;
		ld.variant = (pos != -1) ? ld.name.substring(pos + 1) : null;

		// TODO infer any settings from loc if possible
		
		if (ld.loc != null) 
			ld.rtl = (Character.getDirectionality(ld.loc.getDisplayName(ld.loc).charAt(0)) == Character.DIRECTIONALITY_RIGHT_TO_LEFT);
		
		// TODO load dc specific info about locale 
		// return "true".equals(this.get("rtl"));  
		
		return ld;
	}

	protected Locale loc = null;
	protected String name = null;
	protected String lang = null;
	protected String variant = null;
	protected boolean rtl = false;
	
	public Locale getLocale() {
		return this.loc;
	}
	
	public String getName() {
		return this.name;
	}

	public String getVariant() {
		return this.variant;
	}
	
	public boolean hasVariant() {
		return StringUtil.isNotEmpty(this.variant);
	}
	
	public String getLanguage() {
		return this.lang;
	}
	
	public boolean isRightToLeft() {
		return this.rtl;
	}

	protected LocaleDefinition() {
	}
	
	public boolean match(String locale) {
		return locale.startsWith(this.name);
	}
	
	@Override
	public boolean equals(Object obj) {
		if (obj instanceof LocaleDefinition)
			return this.name.equals(((LocaleDefinition)obj).name);
		
		return super.equals(obj);
	}
}
