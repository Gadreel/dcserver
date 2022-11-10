package dcraft.util.web;

import java.net.IDN;
import java.util.Collection;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * thanks Netty :)
 */
public class DomainNameMapping<V> {
    protected Map<String, V> map = new HashMap<>();

    /*
     * Adds a mapping that maps the specified (optionally wildcard) host name to the specified output value.
     * <p>
     * <a href="http://en.wikipedia.org/wiki/Wildcard_DNS_record">DNS wildcard</a> is supported as hostname.
     * For example, you can use {@code *.netty.io} to match {@code netty.io} and {@code downloads.netty.io}.
     * </p>
     */
    public void add(String hostname, V di) {
        map.put(WebUtil.normalizeDomainName(hostname), di);
    }
    
    public void remove(String hostname) {
        map.remove(WebUtil.normalizeDomainName(hostname));
    }
    
    public Set<String> getDomains() {
    	return this.map.keySet();
    }
    
    public Collection<V> getValues() {
    	return this.map.values();
    }

    public V get(String name) {
        if (name != null) {
        	name = WebUtil.normalizeDomainName(name);
        	
        	// prefer exact matches over wild
        	V exact = map.get(name);
        	
        	if (exact != null)
        		return exact;

            for (Map.Entry<String, V> entry : map.entrySet()) {
                if (WebUtil.matchesDomainName(entry.getKey(), name))
                    return entry.getValue();
            }
        }

        return null;
    }
}

