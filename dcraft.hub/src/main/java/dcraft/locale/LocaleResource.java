package dcraft.locale;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import dcraft.hub.resource.ResourceBase;
import dcraft.hub.resource.ResourceTier;
import dcraft.log.Logger;
import dcraft.util.StringUtil;

public class LocaleResource extends ResourceBase {
	protected Dictionary dictionary = null;
	protected Map<String, LocaleDefinition> locales = new HashMap<>();
	
	// defaults
	protected String chron = null;
	protected String locale = null;
	protected ZoneId chrondef = null;
	protected LocaleDefinition localedef = null;
	
	public LocaleResource() {
		this.setName("Locale");
	}

	public Dictionary getLocalDictionary() {
		return this.dictionary;
	}
	
	public void setLocalDictionary(Dictionary v) {
		this.dictionary = v;
		
		if (this.dictionary == null) {
			Logger.errorTr(104, "Unable to load dictionary file(s)");
			return;
		}

		Logger.trace( "Dictionary loaded");
	}
	
	public LocaleResource getParentResource() {
		if (this.tier == null)
			return null;
		
		ResourceTier pt = this.tier.getParent();
		
		if (pt != null)
			return pt.getLocale();
		
		return null;
	}
	
	public String getDefaultLocale() {
		if (this.locale != null)
			return this.locale;
		
		LocaleResource parent = this.getParentResource();

		if (parent != null)
			return parent.getDefaultLocale();
		
		return null;
	}
	
	public void setDefaultLocale(String v) {
		if (StringUtil.isEmpty(v))
			return;
		
		this.locale = v;
		
		// ready to add definitions
		this.localedef = this.buildLocaleDefinition(this.locale);
		
		// add the list of locales supported for this site
		this.locales.put(this.locale, this.localedef);
	}

	public LocaleDefinition getDefaultLocaleDefinition() {
		if (this.localedef != null)
			return this.localedef;
		
		LocaleResource parent = this.getParentResource();

		if (parent != null)
			return parent.getDefaultLocaleDefinition();
		
		return null;
	}
	
	public String getDefaultChronology() {
		if (this.chron != null)
			return this.chron;
		
		LocaleResource parent = this.getParentResource();

		if (parent != null)
			return parent.getDefaultChronology();
		
		return null;
	}
	
	public void setDefaultChronology(String v) {
		if (StringUtil.isEmpty(v))
			return;
		
		this.chron = v;
		
		this.chrondef = this.getChronologyDefinition(this.chron);
	}
	
	public ZoneId getDefaultChronologyDefinition() {
		if (this.chrondef != null)
			return this.chrondef;
		
		LocaleResource parent = this.getParentResource();

		if (parent != null)
			return parent.getDefaultChronologyDefinition();
		
		return null;
	}

	// means "use this definition"
	public void addLocaleDefinition(LocaleDefinition def) {
		this.locales.put(def.getName(), def);
	}
	
	public LocaleDefinition getLocaleDefinition(String name) {
		LocaleDefinition definition = this.locales.get(name);
		
		if (definition != null)
			return definition;
		
		LocaleResource parent = this.getParentResource();
		
		if (parent != null)
			return parent.getLocaleDefinition(name);
		
		return null;
	}

	public LocaleDefinition buildLocaleDefinition(String name) {
		// TODO lookup definitions
		
		return LocaleDefinition.fromName(name);
	}

	public ZoneId getChronologyDefinition(String name) {
		// TODO lookup definitions
		
		return ZoneId.of(name);
	}

	public List<String> getAlternateLocales() {
		if (this.locales.size() > 0) {
			List<String> alternates = new ArrayList<>();

			for (String locale : locales.keySet()) {
				if (! locale.equals(this.locale))
					alternates.add(locale);
			}

			return alternates;
		}

		LocaleResource parent = this.getParentResource();

		if (parent != null)
			return parent.getAlternateLocales();

		return null;
	}

	/*
	public Map<String, LocaleDefinition> getLocales() {
		if (this.locales.size() == 0) {
			// make sure we have at least 1 locale listed for the site
			String lvalue = this.getDefaultLocale();
			
			// add the list of locales supported for this site
			this.locales.put(lvalue, this.buildLocaleDefinition(lvalue));
		}
		
		return this.locales;
	}
	*/
	
	// 0 is best, higher the number the worse, -1 for not supported
	public int rateLocale(String locale) {
		if ((this.localedef != null) && this.localedef.match(locale))
			return 0;
		
		LocaleResource parent = this.getParentResource();
		
		if (parent != null) {
			int r = parent.rateLocale(locale);
			
			if (r < 0)
				return -1;
			
			return r + 1;
		}
		
		return -1;
	}
	
	public String findToken(String locale, String token) {
		String val = null;
		
		if (this.dictionary != null)
			val = this.dictionary.findToken(locale, token);
		
		if (val == null) {
			LocaleResource parent = this.getParentResource();
			
			if (parent != null)
				return parent.findToken(locale, token);
			
			return token;
		}
		
		return val;
	}
	
	public String tr(String token, Object... params) {
		return this.tr(this.getDefaultLocaleDefinition(), token, params);
	}
	
	public String tr(LocaleDefinition locale, String token, Object... params) {
		String val = this.trVariant(locale.getName(), token, params);
		
		if ((val == null) && locale.hasVariant()) 
			val = this.trVariant(locale.getLanguage(), token, params);
		
		return val;
	}
	
	public String trVariant(String locale, String token, Object... params) {
		String val = null;
		
		if (this.dictionary != null) 
			val = this.dictionary.findToken(locale, token);
		
		if (val == null) {
			LocaleResource parent = this.getParentResource();
			
	    	if (parent != null)
	    		return parent.trVariant(locale, token, params);
	    	
	    	return token;
		}
		
        // the expansion of variables is per Attribute Value Templates in XSLT
        // http://www.w3.org/TR/xslt#attribute-value-templates

        StringBuilder sb = new StringBuilder();

        int lpos = 0;
        int bpos = val.indexOf("{$");

        while (bpos != -1) {
            int epos = val.indexOf("}", bpos);
            if (epos == -1) 
            	break;

            sb.append(val.substring(lpos, bpos));

            lpos = epos + 1;

            String varname = val.substring(bpos + 2, epos).trim();

            // TODO add some formatting features for numbers/datetimes
            
            Long parampos = StringUtil.parseInt(varname);
            
            if ((parampos != null) && (parampos <= params.length)) {
            	if (params[parampos.intValue() - 1] != null)
            		sb.append(params[parampos.intValue() -1].toString());
            }
            else 
                sb.append(val.substring(bpos, epos + 1));

            bpos = val.indexOf("{$", epos);
        }

        sb.append(val.substring(lpos));
		
		return sb.toString();
	}
	
	public String trp(String pluraltoken, String singulartoken, Object... params) {
		return this.trp(this.getDefaultLocaleDefinition(), pluraltoken, singulartoken, params);
	}
	
	public String trp(LocaleDefinition locale, String pluraltoken, String singulartoken, Object... params) {
		if ((params.length > 0) && (params[0] instanceof Number) && (((Number)params[0]).intValue() == 1))
			return this.tr(locale, singulartoken, params);
		
		return this.tr(locale, pluraltoken, params);
	}
}
