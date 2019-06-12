package dcraft.tool.certs;

import dcraft.hub.ResourceHub;
import dcraft.hub.resource.ConfigResource;
import dcraft.hub.resource.ResourceTier;
import dcraft.hub.resource.SslEntry;
import dcraft.hub.resource.TrustResource;
import dcraft.log.Logger;
import dcraft.tenant.Site;
import dcraft.util.StringUtil;
import dcraft.xml.XElement;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.cert.X509Certificate;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class CertUtil {
	static public void loadTierCerts(ConfigResource sconfig, Path scpath, ResourceTier resources) {
		TrustResource trustres = resources.getOrCreateTierTrust();

		trustres.clearSslCerts();

		for (XElement certinfo : sconfig.getTagListLocal("Certificate")) {
			String certname = certinfo.getAttribute("Name");

			if (StringUtil.isNotEmpty(certname)) {
				Path certpath = scpath.resolve(certname);

				if ((certpath == null) || Files.notExists(certpath)) {
					Logger.error("Unable to locate certificate: " + certname);
				}
				else {
					SslEntry entry = SslEntry.ofJks(trustres, certpath,
							certinfo.getAttribute("Password"), certinfo.getAttribute("PlainPassword"));

					if (entry == null) {
						Logger.error("Unable to load certificate: " + certname);
					}
					else {
						trustres.withSsl(entry, certinfo.getAttributeAsBooleanOrFalse("Default"));
					}
				}
			}
		}

		// load standard cert
		{
			Path certpath = scpath.resolve("certs.jks");

			if (Files.exists(certpath)) {
				SslEntry entry = SslEntry.ofJks(trustres, certpath,null, new String(ResourceHub.getResources().getKeyRing().getPassphrase()));

				if (entry == null) {
					Logger.error("Unable to load default certificate");
				}
				else {
					trustres.withSsl(entry, false);
				}
			}
		}
	}

	static public List<X509Certificate> getPastDueTopCerts(ZonedDateTime checkdate) {
		return CertUtil.getPastDueCerts(ResourceHub.getTopResources(), checkdate);
	}

	static public List<X509Certificate> getPastDueSiteCerts(Site site, ZonedDateTime checkdate) {
		return CertUtil.getPastDueCerts(site.getTierResources(), checkdate);
	}

	static public List<X509Certificate> getPastDueCerts(ResourceTier tier, ZonedDateTime checkdate) {
		List<X509Certificate> list = new ArrayList<>();

		TrustResource trustResource = tier.getTrust();

		if (trustResource != null) {
			for (SslEntry sslEntry : trustResource.getTierSslCerts()) {
				for (X509Certificate cert : sslEntry.getIssuedCerts()) {
					if (cert.getNotAfter().toInstant().isBefore(checkdate.toInstant())) {
						list.add(cert);
					}
				}
			}
		}

		return list;
	}
}
