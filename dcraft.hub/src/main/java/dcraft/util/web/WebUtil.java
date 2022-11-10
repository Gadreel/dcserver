package dcraft.util.web;

import java.net.IDN;
import java.util.Locale;
import java.util.regex.Pattern;

public class WebUtil {
    protected static final Pattern DNS_WILDCARD_PATTERN = Pattern.compile("^\\*\\..*");

    /**
     * Simple function to match <a href="http://en.wikipedia.org/wiki/Wildcard_DNS_record">DNS wildcard</a>.
     */
    static public boolean matchesDomainName(String hostNameTemplate, String hostName) {
        // note that inputs are converted and lowercased already
        if (DNS_WILDCARD_PATTERN.matcher(hostNameTemplate).matches())
            return hostNameTemplate.substring(2).equals(hostName) || hostName.endsWith(hostNameTemplate.substring(1));

        return hostNameTemplate.equals(hostName);
    }

    /**
     * IDNA ASCII conversion and case normalization
     * see also https://www.punycoder.com/
     */
    static public String normalizeDomainName(String hostname) {
        // as a means of normalization we want to lowercase the core ascii chars as well - in case people get funky with their domain name entry
        hostname = WebUtil.toASCIILower(hostname);

        // punycode will already lowercase some of the unicode parts - for better or worse
        return IDN.toASCII(hostname, IDN.ALLOW_UNASSIGNED);
    }

    static public char toASCIILower(char ch){
        if(('A' <= ch) && (ch <= 'Z'))
            return (char)(ch + 'a' - 'A');

        return ch;
    }

    static public String toASCIILower(String input){
        StringBuilder dest = new StringBuilder();

        for (int i = 0; i < input.length(); i++){
            dest.append(WebUtil.toASCIILower(input.charAt(i)));
        }

        return dest.toString();
    }
}
