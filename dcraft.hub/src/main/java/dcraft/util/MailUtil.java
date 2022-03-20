package dcraft.util;

import dcraft.log.Logger;

import javax.mail.internet.AddressException;
import javax.mail.internet.InternetAddress;

public class MailUtil {
    static public String cleanEmailDomainName(String address) {
        try {
            InternetAddress[] toaddrs = InternetAddress.parse(address);        // this should be clean now because it went through SmtpWork or similar

            // https://en.wikipedia.org/wiki/Internationalized_domain_name

            address = toaddrs[0].getAddress();

            int dpos = address.indexOf('@');

            return address.substring(0, dpos) + "@" + address.substring(dpos + 1).toLowerCase();		// put domain into proper casing, especially for sub key
        }
        catch (AddressException x) {
            Logger.warn("Unexpected parse error with address: " + address + " - " + x);

            return null;
        }
    }
}
