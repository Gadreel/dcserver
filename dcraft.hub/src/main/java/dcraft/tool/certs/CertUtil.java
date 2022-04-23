package dcraft.tool.certs;

import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.SftpException;
import dcraft.tool.release.ServerHelper;
import dcraft.hub.ResourceHub;
import dcraft.hub.app.ApplicationHub;
import dcraft.hub.resource.ResourceTier;
import dcraft.hub.resource.SslEntry;
import dcraft.hub.resource.TrustResource;
import dcraft.log.Logger;
import dcraft.tenant.Site;
import dcraft.util.IOUtil;
import dcraft.util.StringUtil;
import dcraft.xml.XElement;
import org.bouncycastle.asn1.x500.RDN;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x500.style.BCStyle;
import org.bouncycastle.asn1.x500.style.IETFUtils;
import org.bouncycastle.cert.jcajce.JcaX509CertificateHolder;
import org.shredzone.acme4j.*;
import org.shredzone.acme4j.challenge.Challenge;
import org.shredzone.acme4j.challenge.Dns01Challenge;
import org.shredzone.acme4j.challenge.Http01Challenge;
import org.shredzone.acme4j.exception.AcmeException;
import org.shredzone.acme4j.util.KeyPairUtils;

import java.io.*;
import java.net.URI;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateParsingException;
import java.security.cert.X509Certificate;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Scanner;

public class CertUtil {
	static public void loadTierCerts(Path scpath, ResourceTier resources) {
		TrustResource trustres = resources.getOrCreateTierTrust();

		trustres.clearSslCerts();

		for (XElement certinfo : resources.getConfig().getTagListLocal("Certificate")) {
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
	
	static public List<String> getNames(X509Certificate certificate) {
		List<String> names = getSubjectAlternativeNames(certificate);
		
		String scn = getSubjectName(certificate);
		
		if (! names.contains(scn))
			names.add(scn);
		
		return names;
	}
	
	public static String getSubjectName(X509Certificate certificate) {
		try {
			X500Name x500name = new JcaX509CertificateHolder(certificate).getSubject();
			RDN cn = x500name.getRDNs(BCStyle.CN)[0];
			return IETFUtils.valueToString(cn.getFirst().getValue());
		}
		catch (CertificateEncodingException x) {
			return null;
		}
	}
	
	public static List<String> getSubjectAlternativeNames(X509Certificate certificate) {
		List<String> identities = new ArrayList<>();
		
		try {
			Collection<List<?>> altNames = certificate.getSubjectAlternativeNames();
			
			if (altNames == null)
				return identities;
			
			for (List item : altNames) {
				Integer type = (Integer) item.get(0);
				
				if (type == 0 || type == 2) {
					if (item.toArray()[1] instanceof String) {
						identities.add((String) item.toArray()[1]);
						continue;
					}
					
					/*
					try {
						if(item.toArray()[1] instanceof byte[]) {
							ASN1InputStream decoder = new ASN1InputStream((byte[]) item.toArray()[1]);
							
							ASN1Primitive encoded = decoder.readObject();
							
							// problems here
							DEREncodableVector encoded = decoder.readObject();
							encoded = ((DERSequence) encoded).getObjectAt(1);
							encoded = ((DERTaggedObject) encoded).getObject();
							encoded = ((DERTaggedObject) encoded).getObject();
							
							String identity = ((DERUTF8String) encoded).getString();
							identities.add(identity);
						}
					}
					catch (UnsupportedEncodingException e) {
						Logger.error("Error decoding subjectAltName" + e.getLocalizedMessage());
					}
					catch (Exception e) {
						Logger.error("Error decoding subjectAltName" + e.getLocalizedMessage());
					}
					*/
				}
				else{
					Logger.warn("SubjectAltName of invalid type found: " + certificate);
				}
			}
		}
		catch (CertificateParsingException e) {
			Logger.error("Error parsing SubjectAltName in certificate: " + certificate + "\r\nerror:" + e.getLocalizedMessage());
		}
		
		return identities;
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

	/* BEGIN ACME */

	// Create a session for Let's Encrypt.
	static public Session newSession(boolean live) {
		// Use "acme://letsencrypt.org" for production server
		return new org.shredzone.acme4j.Session(live ? "acme://letsencrypt.org" : "acme://letsencrypt.org/staging");
	}
	
	static public URI termsOfServiceLink(boolean live) {
		try {
			return newSession(live).getMetadata().getTermsOfService();
		}
		catch (AcmeException x) {
			Logger.error("Unable to get terms of service link");
		}
		
		return null;
	}
	
	static public boolean hasUserKeyPair() {
		Path userkey = ApplicationHub.getDeploymentPath().resolve("config/acme.key");

		return Files.exists(userkey);
	}
	
	static public KeyPair loadUserKeyPair() throws IOException {
		Path userkey = ApplicationHub.getDeploymentPath().resolve("config/acme.key");
		
		if (Files.exists(userkey)) {
			// If there is a key file, read it
			try (FileReader fr = new FileReader(userkey.toFile())) {
				return KeyPairUtils.readKeyPair(fr);
			}
		}
		
		return null;
	}

	static public KeyPair loadOrCreateUserKeyPair() throws IOException {
		Path userkey = ApplicationHub.getDeploymentPath().resolve("config/acme.key");

		if (Files.exists(userkey)) {
			// If there is a key file, read it
			try (FileReader fr = new FileReader(userkey.toFile())) {
				return KeyPairUtils.readKeyPair(fr);
			}
		}
		else {
			// If there is none, create a new key pair and save it
			KeyPair userpair = KeyPairUtils.createKeyPair(2048);

			try (FileWriter fw = new FileWriter(userkey.toFile())) {
				KeyPairUtils.writeKeyPair(userpair, fw);
			}

			return userpair;
		}
	}

	static public Account checkAccount(Session session, KeyPair accountKey) {
		if (accountKey == null)
			return null;
		
		try {
			Account account = new AccountBuilder()
					.onlyExisting()         // Do not create a new account
					.useKeyPair(accountKey)
					.create(session);
			
			URL accountLocationUrl = account.getLocation();
			
			if (accountLocationUrl != null)
				return account;
		}
		catch (AcmeException x) {
		}

		return null;
	}

	static public Account findOrRegisterAccount(Session session, KeyPair accountKey, Scanner scanner) throws AcmeException {
		// try to use existing account first
		try {
			Account account = new AccountBuilder()
					.onlyExisting()         // Do not create a new account
					.useKeyPair(accountKey)
					.create(session);
			
			URL accountLocationUrl = account.getLocation();
			
			if (accountLocationUrl != null)
				return account;
		}
		catch (AcmeException x) {
			// ignore - go on to create
		}
		
		// Ask the user to accept the TOS, if server provides us with a link.
		URI tos = session.getMetadata().getTermsOfService();

		if (tos != null) {
			acceptAgreement(tos, scanner);
		}

		Account account = new AccountBuilder()
				.agreeToTermsOfService()
				.useKeyPair(accountKey)
				.create(session);

		Logger.info("Registered a new user, URL: " + account.getLocation());

		return account;
	}

	static public void acceptAgreement(URI agreement, Scanner scanner) throws AcmeException {
		// background job, don't prompt
		if (scanner == null)
			return;

		System.out.println("Do you accept the Terms of Service?\n\n" + agreement);

		String resp = scanner.nextLine().trim().toLowerCase();

		if (resp.startsWith("y"))
			return;

		throw new AcmeException("User did not accept Terms of Service");
	}

	static public boolean authorize(Authorization auth, Scanner scan, ServerHelper ssh, ChannelSftp sftp, Path sitepath) throws AcmeException {
		Logger.info("Authorization for domain " + auth.getIdentifier().getDomain());

		// The authorization is already valid. No need to process a challenge.
		if (auth.getStatus() == Status.VALID) {
			return true;
		}

		// Find the desired challenge and prepare it.
		Challenge challenge = httpChallenge(auth, scan, ssh, sftp, sitepath);

		/*
		switch (CHALLENGE_TYPE) {
			case HTTP:
				challenge = httpChallenge(auth, scan);
				break;

			case DNS:
				challenge = dnsChallenge(auth, scan);
				break;
		}

		if (challenge == null) {
			throw new AcmeException("No challenge found");
		}
		*/

		// If the challenge is already verified, there's no need to execute it again.
		if (challenge == null) {
			return false;
		}

		if (challenge.getStatus() == Status.VALID) {
			return true;
		}

		// Now trigger the challenge.
		challenge.trigger();

		// Poll for the challenge to complete.
		try {
			int attempts = 10;
			while (challenge.getStatus() != Status.VALID && attempts-- > 0) {
				// Did the authorization fail?
				if (challenge.getStatus() == Status.INVALID) {
					throw new AcmeException("Challenge failed... Giving up.");
				}

				// Wait for a few seconds
				Thread.sleep(3000L);

				// Then update the status
				challenge.update();
			}
		} catch (InterruptedException x) {
			Logger.error("interrupted: " + x);
			Thread.currentThread().interrupt();
			return false;
		}

		// All reattempts are used up and there is still no valid authorization?
		if (challenge.getStatus() != Status.VALID) {
			throw new AcmeException("Failed to pass the challenge for domain "
					+ auth.getIdentifier().getDomain() + ", ... Giving up.");
		}

		Logger.info("Challenge has been completed. Remember to remove the validation resource.");
		return true;
	}

	static public boolean authorize(Authorization auth, Scanner scan, Path sitepath) throws AcmeException {
		Logger.info("Authorization for domain " + auth.getIdentifier().getDomain());

		// The authorization is already valid. No need to process a challenge.
		if (auth.getStatus() == Status.VALID) {
			return true;
		}

		// Find the desired challenge and prepare it.
		Challenge challenge = httpChallenge(auth, scan, sitepath);

		/*
		switch (CHALLENGE_TYPE) {
			case HTTP:
				challenge = httpChallenge(auth, scan);
				break;

			case DNS:
				challenge = dnsChallenge(auth, scan);
				break;
		}

		if (challenge == null) {
			throw new AcmeException("No challenge found");
		}
		*/

		// If the challenge is already verified, there's no need to execute it again.
		if (challenge == null) {
			return false;
		}

		if (challenge.getStatus() == Status.VALID) {
			return true;
		}

		// Now trigger the challenge.
		challenge.trigger();

		// Poll for the challenge to complete.
		try {
			int attempts = 10;
			while (challenge.getStatus() != Status.VALID && attempts-- > 0) {
				// Did the authorization fail?
				if (challenge.getStatus() == Status.INVALID) {
					throw new AcmeException("Challenge failed... Giving up.");
				}

				// Wait for a few seconds
				Thread.sleep(3000L);

				// Then update the status
				challenge.update();
			}
		} catch (InterruptedException x) {
			Logger.error("interrupted: " + x);
			Thread.currentThread().interrupt();
			return false;
		}

		// All reattempts are used up and there is still no valid authorization?
		if (challenge.getStatus() != Status.VALID) {
			throw new AcmeException("Failed to pass the challenge for domain "
					+ auth.getIdentifier().getDomain() + ", ... Giving up.");
		}

		Logger.info("Challenge has been completed. Remember to remove the validation resource.");
		return true;
	}

	static public Challenge httpChallenge(Authorization auth, Scanner scan, ServerHelper ssh, ChannelSftp sftp, Path sitepath) throws AcmeException {
		// Find a single http-01 challenge
		Http01Challenge challenge = auth.findChallenge(Http01Challenge.TYPE);
		if (challenge == null) {
			throw new AcmeException("Found no " + Http01Challenge.TYPE + " challenge, don't know what to do...");
		}

		// Output the challenge, wait for acknowledge...
		Logger.info("Please create a file in your web server's base directory.");
		Logger.info("It must be reachable at: http://" + auth.getIdentifier().getDomain() + "/.well-known/acme-challenge/"
						+ challenge.getToken());
		Logger.info("File name: " + challenge.getToken());
		Logger.info("Content: " + challenge.getAuthorization());
		Logger.info("The file must not contain any leading or trailing whitespaces or line breaks!");

		StringBuilder message = new StringBuilder();
		message.append("Please create a file in your web server's base directory.\n\n");
		message.append("http://")
				.append(auth.getIdentifier().getDomain())
				.append("/.well-known/acme-challenge/")
				.append(challenge.getToken())
				.append("\n\n");
		message.append("Content:\n\n");
		message.append(challenge.getAuthorization());

		if (scan != null) {
			System.out.println();
			System.out.println(message.toString());
			System.out.println();
			//System.out.println("If you're ready, press ENTER...");
		}

		Path remotepath = sitepath.resolve(".well-known/acme-challenge");

		if (scan != null) {
			System.out.println("Dest: " + remotepath.toString());
		}

		ssh.makeDirSftp(sftp, remotepath);

		InputStream is = new ByteArrayInputStream(challenge.getAuthorization().getBytes(Charset.forName("UTF-8")));

		String token = remotepath.resolve(challenge.getToken()).toString();

		if (scan != null) {
			System.out.println("Token Dest: " + token);
		}

		try {
			sftp.put(is, token, ChannelSftp.OVERWRITE);
			sftp.chmod(420, token);        // 644 octal = 420 dec, 744 octal = 484 dec

			Logger.info("Challenge uploaded");
		}
		catch (SftpException x) {
			Logger.error("Error uploading challenge: " + x);
		}

		if (scan != null) {
			System.out.println("Review and then press ENTER...");
			scan.nextLine();
		}
		else {
			try {
				Thread.sleep(1000);
			}
			catch (InterruptedException x) {
			}
		}

		return challenge;
	}

	static public Challenge httpChallenge(Authorization auth, Scanner scan, Path sitepath) throws AcmeException {
		// Find a single http-01 challenge
		Http01Challenge challenge = auth.findChallenge(Http01Challenge.TYPE);
		if (challenge == null) {
			throw new AcmeException("Found no " + Http01Challenge.TYPE + " challenge, don't know what to do...");
		}

		// Output the challenge, wait for acknowledge...
		Logger.info("Please create a file in your web server's base directory.");
		Logger.info("It must be reachable at: http://" + auth.getIdentifier().getDomain() + "/.well-known/acme-challenge/"
						+ challenge.getToken());
		Logger.info("File name: " + challenge.getToken());
		Logger.info("Content: " + challenge.getAuthorization());
		Logger.info("The file must not contain any leading or trailing whitespaces or line breaks!");

		StringBuilder message = new StringBuilder();
		message.append("Please create a file in your web server's base directory.\n\n");
		message.append("http://")
				.append(auth.getIdentifier().getDomain())
				.append("/.well-known/acme-challenge/")
				.append(challenge.getToken())
				.append("\n\n");
		message.append("Content:\n\n");
		message.append(challenge.getAuthorization());

		if (scan != null) {
			System.out.println();
			System.out.println(message.toString());
			System.out.println();
			//System.out.println("If you're ready, press ENTER...");
		}
		
		Path token = sitepath.resolve(".well-known/acme-challenge").resolve(challenge.getToken());

		if (scan != null) {
			System.out.println("Token Dest: " + token);
		}

		if (IOUtil.saveEntireFile(token, challenge.getAuthorization())) {
			Logger.info("Challenge uploaded");
		}
		else {
			Logger.error("Error writing challenge");
			return null;
		}

		if (scan != null) {
			System.out.println("Review and then press ENTER...");
			scan.nextLine();
		}
		else {
			try {
				Thread.sleep(1000);
			}
			catch (InterruptedException x) {
			}
		}

		return challenge;
	}

	static public Challenge dnsChallenge(Authorization auth, Scanner scan) throws AcmeException {
		// Find a single dns-01 challenge
		Dns01Challenge challenge = auth.findChallenge(Dns01Challenge.TYPE);
		if (challenge == null) {
			throw new AcmeException("Found no " + Dns01Challenge.TYPE + " challenge, don't know what to do...");
		}

		// Output the challenge, wait for acknowledge...
		Logger.info("Please create a TXT record:");
		Logger.info("_acme-challenge. " + auth.getIdentifier().getDomain() + ". IN TXT " + challenge.getDigest());
		Logger.info("If you're ready, press enter...");

		StringBuilder message = new StringBuilder();
		message.append("Please create a TXT record:\n\n");
		message.append("_acme-challenge.")
				.append(auth.getIdentifier().getDomain())
				.append(". IN TXT ")
				.append(challenge.getDigest());

		if (scan != null) {
			System.out.println();
			System.out.println(message.toString());

			scan.nextLine();
		}
		else {
			try {
				Thread.sleep(1000);
			}
			catch (InterruptedException x) {
			}
		}

		return challenge;
	}

	static public KeyPair loadOrCreateDomainKeyPair(Path siteconfig) throws IOException {
		Path certspath = siteconfig.resolve("certs.key");

		if (Files.exists(certspath)) {
			try (FileReader fr = new FileReader(certspath.toFile())) {
				return KeyPairUtils.readKeyPair(fr);
			}
		}
		else {
			KeyPair domainKeyPair = KeyPairUtils.createKeyPair(2048);
			try (FileWriter fw = new FileWriter(certspath.toFile())) {
				KeyPairUtils.writeKeyPair(domainKeyPair, fw);
			}
			return domainKeyPair;
		}
	}

	/* END ACME */
}
