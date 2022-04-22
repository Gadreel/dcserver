package dcraft.hub;

import dcraft.hub.op.OperationContext;
import dcraft.hub.resource.ResourceTier;
import dcraft.locale.LocaleResource;

public class ResourceHub {
  	static protected ResourceTier top = ResourceTier.top();
	
	static public ResourceTier getTopResources() {
		return ResourceHub.top;
	}
	
	static public void setTopResources(ResourceTier v) {
		ResourceHub.top = v;
	}
	
	static public ResourceTier getResources() {
		OperationContext oc = OperationContext.getOrNull();
		
		if (oc != null)
			return oc.getResources();
		
		return ResourceHub.top;
	}

	static public ResourceTier getSiteResources() {
		OperationContext oc = OperationContext.getOrNull();

		if (oc != null)
			return oc.getSite().getResources();

		return ResourceHub.top;
	}

	static public ResourceTier getTenantResources() {
		OperationContext oc = OperationContext.getOrNull();

		if (oc != null)
			return oc.getTenant().getResources();

		return ResourceHub.top;
	}

	static public String tr(long code, Object... params) {
		return ResourceHub.tr("_code_" + code, params);
	}
	
	/**
	 * translate the token and parameters using the current or default locale
	 * 
	 * @param token name of the dictionary item to lookup
	 * @param params parameters to use when formatting the output
	 * @return translated and formatted text
	 */
	static public String tr(String token, Object... params) {
		ResourceTier tr = ResourceHub.getResources();
		
		if (tr == null)
			return token;

		LocaleResource lr = tr.getLocale();
		
		if (lr == null)
			return token;
		
		return lr.tr(token, params);
	}
	
	static public String trSys(String token, Object... params) {
		ResourceTier tr = ResourceHub.top;
		
		if (tr == null)
			return token;

		LocaleResource lr = tr.getLocale();
		
		if (lr == null)
			return token;
		
		return lr.tr(token, params);
	}
	
	static public String trp(long pluralcode, long singularcode, Object... params) {
		return ResourceHub.trp("_code_" + pluralcode, "_code_" + singularcode, params);
	}
		
	/*
	 * translate a token and parameters using the current or default locale.
	 * if the first parameter is numeric and 1 then use the singular token
	 * otherwise lookup the plural token
	 * 
	 * @param pluraltoken name of the dictionary item to lookup
	 * @param singulartoken name of the dictionary item to lookup
	 * @param params parameters to use when formatting the output
	 * @return translated and formatted text
	 */
	static public String trp(String pluraltoken, String singulartoken, Object... params) {
		ResourceTier tr = ResourceHub.getResources();
		
		if (tr == null)
			return singulartoken;

		LocaleResource lr = tr.getLocale();
		
		if (lr == null)
			return singulartoken;

		return lr.trp(pluraltoken, singulartoken, params);
	}
}
