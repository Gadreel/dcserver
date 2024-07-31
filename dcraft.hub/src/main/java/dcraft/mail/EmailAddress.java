package dcraft.mail;

import dcraft.log.Logger;
import dcraft.util.HashUtil;
import dcraft.util.StringUtil;
import dcraft.util.web.WebUtil;

import javax.mail.internet.AddressException;
import javax.mail.internet.InternetAddress;
import java.util.ArrayList;
import java.util.List;

// currently going along with l3x answer https://stackoverflow.com/questions/9807909/are-email-addresses-case-sensitive
// but only for accounts with 7 bit ascii
// three modes - display (normal), transport (for the email service), index (for duplicates checking)
public class EmailAddress {
    static public List<EmailAddress> parseList(String addresses) {
        if (StringUtil.isEmpty(addresses)) {
            Logger.warn("Email address is missing");
            return null;
        }

        // support ; or , as address separator
        addresses = addresses.replace(';', ',');

        try {
            // TODO either update javamail or replace so that we can support ALL special characters in domains, for example
            // does not currently support the valid address andy@\uD834\uDD1E-\uD834\uDD31-\uD834\uDD2B.designcraft.io
            InternetAddress[] toaddrs = InternetAddress.parse(addresses);

            if ((toaddrs == null) || (toaddrs.length == 0)) {
                Logger.warn("Email address is not valid: " + addresses);
                return null;
            }

            return EmailAddress.wrap(toaddrs);
        }
        catch (AddressException | IndexOutOfBoundsException | NullPointerException x) {
            Logger.warn("Email address is not valid: " + addresses + " - " + x);
            return null;
        }
    }

    static public EmailAddress parseSingle(String addresses) {
        if (StringUtil.isEmpty(addresses)) {
            Logger.warn("Email address is missing");
            return null;
        }

        // support ; or , as address separator
        addresses = addresses.replace(';', ',');

        try {
            // TODO either update javamail or replace so that we can support ALL special characters in domains, for example
            // does not currently support the valid address andy@\uD834\uDD1E-\uD834\uDD31-\uD834\uDD2B.designcraft.io
            InternetAddress[] toaddrs = InternetAddress.parse(addresses);

            if ((toaddrs == null) || (toaddrs.length == 0)) {
                Logger.warn("Email address is not valid: " + addresses);
                return null;
            }

            return EmailAddress.wrap(toaddrs[0]);
        }
        catch (AddressException | IndexOutOfBoundsException | NullPointerException x) {
            Logger.warn("Email address is not valid: " + addresses + " - " + x);
            return null;
        }
    }

    static public List<EmailAddress> wrap(InternetAddress[] addresses) {
        if (addresses == null)
            return null;

        List<EmailAddress> wrapped = new ArrayList<>();

        for (int i = 0; i < addresses.length; i++)
            wrapped.add(EmailAddress.wrap(addresses[i]));

        return wrapped;
    }

    static public EmailAddress wrap(InternetAddress address) {
        if (address == null)
            return null;

        EmailAddress emailAddress = new EmailAddress();
        emailAddress.mailaddress = address;
        return emailAddress;
    }

    protected InternetAddress mailaddress = null;

    public InternetAddress getMailAddress() {
        return this.mailaddress;
    }

    // put domain into proper casing / idn
    // https://en.wikipedia.org/wiki/Internationalized_domain_name

    public String toStringForTransport(String provider) {
        String fulladdress = this.mailaddress.getAddress();

        // would not be a valid address (non-null object) if @ was not present - safe to assume
        int dpos = fulladdress.indexOf('@');
        String local = fulladdress.substring(0, dpos);
        String domain = fulladdress.substring(dpos + 1);

        // TODO enhance to encode local part for different email providers in transport

        // TODO all sorts of escaping could happen for personal and local

        if (StringUtil.isNotEmpty(this.mailaddress.getPersonal()))
            return this.mailaddress.getPersonal() + " <" + local + "@" + WebUtil.normalizeDomainName(domain) + ">";

        return local + "@" + WebUtil.normalizeDomainName(domain);
    }

    public String toStringNormalized() {
        String fulladdress = this.mailaddress.getAddress();

        // would not be a valid address (non-null object) if @ was not present - safe to assume
        int dpos = fulladdress.indexOf('@');
        String local = fulladdress.substring(0, dpos);
        String domain = fulladdress.substring(dpos + 1);

        return local + "@" + WebUtil.normalizeDomainName(domain);
    }

    // convert to 7 bit compatible
    public String toStringForIndex() {
        String fulladdress = this.mailaddress.getAddress();

        int dpos = fulladdress.indexOf('@');
        String local = fulladdress.substring(0, dpos);
        String domain = fulladdress.substring(dpos + 1);

        // some mail providers may support mixed case email address, if we run into that we
        // should consider a domain lookup in the dcTenant to see if that domain supports mixed casing
        // and then skip the below

        //local = WebUtil.toASCIILower(local);
        // treat like a domain in terms of indexing - meaning each . is a separate section
        local = WebUtil.normalizeDomainName(local);

        // put domain into proper casing, especially for sub key
        domain = WebUtil.normalizeDomainName(domain);

        String cleaned = local + "@" + domain;

        if (StringUtil.isNotEmpty(cleaned) && (cleaned.length() > 1000))
            cleaned = HashUtil.getSha256(cleaned);

        return cleaned;
    }

    public boolean containsDomain(String domain) {
        String fulladdress = this.mailaddress.getAddress();

        return fulladdress.contains(domain);
    }

    public boolean hasPersonal() {
        return (this.mailaddress.getPersonal() != null);
    }

    public String getPersonal() {
        return this.mailaddress.getPersonal();
    }
}
