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
package dcraft.hub.resource;

import dcraft.hub.app.ApplicationHub;
import dcraft.tool.certs.CertUtil;
import io.netty.handler.ssl.*;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.KeyStore;
import java.security.KeyStore.Entry;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.cert.CertificateParsingException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Enumeration;
import java.util.List;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLServerSocketFactory;
import javax.net.ssl.TrustManager;

import io.netty.handler.ssl.util.SelfSignedCertificate;
import org.bouncycastle.asn1.x500.RDN;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x500.style.BCStyle;
import org.bouncycastle.asn1.x500.style.IETFUtils;
import org.bouncycastle.cert.jcajce.JcaX509CertificateHolder;

import dcraft.log.Logger;
import dcraft.util.KeyUtil;
import dcraft.util.StringUtil;

public class SslEntry {
	static public SslEntry ofSelfSignedCert(TrustResource trust) {
		SslEntry entry = new SslEntry();
		
		entry.loadSelfSignedCert(trust);
		
		return entry;
	}
	
	static public SslEntry ofJks(TrustResource trust, Path jksfile, String encpassword, String plainpassword) {
		SslEntry entry = new SslEntry();
		
		entry.load(trust, jksfile, encpassword, plainpassword);
		
		return entry;
	}
	
    protected KeyManagerFactory keyman = null;
    protected List<String> keynames = new ArrayList<>();
    protected List<X509Certificate> issuedCerts = new ArrayList<>();

    public boolean keynameMatch(String name) {
    	for (String kname : this.keynames)
    		if (kname.equals(name))
    			return true;
    	
    	int p = name.indexOf('.');
    	
    	name = name.substring(p + 1);
    	
    	for (String kname : this.keynames)
    		if (kname.equals("*." + name))
    			return true;
    	
    	return false;
    }
	
	public List<X509Certificate> getIssuedCerts() {
		return this.issuedCerts;
	}
    
    public SslContextBuilder getServerBuilder(TrustResource trust) {
		ApplicationProtocolConfig apn = new ApplicationProtocolConfig(
				ApplicationProtocolConfig.Protocol.ALPN,
				// NO_ADVERTISE is currently the only mode supported by both OpenSsl and JDK providers.
				ApplicationProtocolConfig.SelectorFailureBehavior.NO_ADVERTISE,
				// ACCEPT is currently the only mode supported by both OpenSsl and JDK providers.
				ApplicationProtocolConfig.SelectedListenerFailureBehavior.ACCEPT,
				// TODO ApplicationProtocolNames.HTTP_2,
				ApplicationProtocolNames.HTTP_1_1);
	
		return SslContextBuilder.forServer(this.keyman)
				//.ciphers(CIPHERS, SupportedCipherSuiteFilter.INSTANCE)
				.ciphers(trust.getCiphers())
				.protocols(trust.getProtocols().toArray(new String[0]))
				.applicationProtocolConfig(apn)
				.sslProvider(SslProvider.OPENSSL);
	}
	
	public void loadSelfSignedCert(TrustResource trust) {
    	try {
			SelfSignedCertificate ssc = new SelfSignedCertificate();
		
			KeyStore ks = KeyStore.getInstance("JKS");
			ks.setCertificateEntry("host", ssc.cert());
			ks.setKeyEntry("host", ssc.key(), null, new Certificate[] { ssc.cert() });
		
			KeyManagerFactory kmf = KeyManagerFactory.getInstance(trust.getAlgorithm());
			kmf.init(ks, null);
		
			this.keyman = kmf;
			
			Logger.info("Self signed cert initialized");
		}
		catch (Exception x) {
    		Logger.error("Unable to create self signed cert");
		}
	}
    
    public void load(TrustResource trust, Path jksfile, String encpassword, String plainpassword) {
    	//if (sslconfig == null)
    	//	return;
    	//
        //String algorithm = sslconfig.getAttribute("Algorithm", "SunX509");
        //String protocol = sslconfig.getAttribute("Protocol", "TLSv1.2");
        
        //String jksfile = sslconfig.getAttribute("File");
        
        //if (StringUtil.isNotEmpty(prepath))
        //	jksfile = prepath + jksfile;
        
        //String jkspass = sslconfig.getAttribute("Password");

        if (StringUtil.isNotEmpty(encpassword)) {
	        // try to decrypt, if we succeed then we use it - if we don't then use plain text password
			plainpassword = ApplicationHub.getClock().getObfuscator().decryptHexToString(encpassword);
        }
        //else {
        //   jkspass = sslconfig.getAttribute("PlainPassword");
        //}
        
        if (Files.notExists(jksfile)) {
        	Logger.error("Cannot load key file, does not exist: " + jksfile);
			return;
		}
		
        try (InputStream jksin = Files.newInputStream(jksfile, StandardOpenOption.READ)) {
        	// load keystore into key man
            KeyStore ks = KeyStore.getInstance("JKS");
            ks.load(jksin, plainpassword.toCharArray());
	
			KeyManagerFactory kmf = KeyManagerFactory.getInstance(trust.getAlgorithm());
			kmf.init(ks, plainpassword.toCharArray());
	
			this.keyman = kmf;

			// get domain names and show information about the cert
			
			Enumeration<String> aliases = ks.aliases();
			
			Logger.debug("Certs and keys in web server key store:");
			
			while (aliases.hasMoreElements()) {
			  String alias = aliases.nextElement();
			  
			  if (ks.isCertificateEntry(alias)) {
				  Entry e = ks.getEntry(alias, null);
				  KeyStore.TrustedCertificateEntry certentry = (KeyStore.TrustedCertificateEntry) e;
				  //Logger.info("Trusted Cert: " + alias + " " + certentry.getTrustedCertificate().getType());
				  
				  CertificateFactory certFactory = CertificateFactory.getInstance("X.509");
				  InputStream in = new ByteArrayInputStream(certentry.getTrustedCertificate().getEncoded());
				  X509Certificate cert = (X509Certificate)certFactory.generateCertificate(in);
				  
				  String subject = cert.getSubjectDN().toString();
				  String thumbprint = KeyUtil.getCertThumbprint(cert);
				  
				  Logger.debug("Trusted Cert: " + alias + " Subject: " + subject + " Thumbprint: " + thumbprint);
			  }
			  else if (ks.isKeyEntry(alias)) {
				  Entry e = ks.getEntry(alias, new KeyStore.PasswordProtection(plainpassword.toCharArray()));
				  KeyStore.PrivateKeyEntry keyentry = (KeyStore.PrivateKeyEntry) e;
				  
				  //String thumb = HashUtil.getCertThumbprint(keyentry.getCertificate());
				  
				  //Logger.info("Key: " + thumb + " / " + keyentry.getPrivateKey().getFormat() + " / " + keyentry.getPrivateKey().getAlgorithm());
				  
				  CertificateFactory certFactory = CertificateFactory.getInstance("X.509");
				  InputStream in = new ByteArrayInputStream(keyentry.getCertificate().getEncoded());
				  X509Certificate cert = (X509Certificate)certFactory.generateCertificate(in);
				  
				  String subject = cert.getSubjectDN().toString();
				  String thumbprint = KeyUtil.getCertThumbprint(cert);
				  
				  
				  //Logger.info("Key: " + subject + " : " + thumbprint);
				  Logger.debug("Key: " + alias + " Subject: " + subject + " Thumbprint: " + thumbprint);
				  
				  this.issuedCerts.add(cert);
				  
				  this.keynames.addAll(CertUtil.getNames(cert));
				  
					  /*
					  if ((key instanceof PrivateKey) && "PKCS#8".equals(key.getFormat())) {
						// Most PrivateKeys use this format, but check for safety.
						try (FileOutputStream os = new FileOutputStream(alias + ".key")) {
						  os.write(key.getEncoded());
						  os.flush();
						}
					  }
					   */
			  }
			}
	
			// show info about the context that can be created from this
			
			if (Logger.isDebug()) {
				// init server context  TODO show 1.3 too
				SSLContext serverContext = SSLContext.getInstance("TLSv1.2");
				serverContext.init(kmf.getKeyManagers(), trust.getAllTrustCerts().toArray(new TrustManager[0]), null);
            
            	Logger.debug("TLS Provider: " + serverContext.getProvider().getName());
            	Logger.debug("TLS Protocol: " + serverContext.getProtocol());
            	
            	SSLServerSocketFactory sfactory = serverContext.getServerSocketFactory();
            	
            	Logger.debug("Default Suites");
		        
		        for (String p : sfactory.getDefaultCipherSuites())
		        	Logger.debug("Suite: " + p);
		        
		        Logger.debug("Supported Suites");
		        
		        for (String p : sfactory.getSupportedCipherSuites())
		        	Logger.debug("Suite: " + p);
				
				Logger.debug("OpenSSL in use (web): " + OpenSsl.isAvailable());
            }
        }
        catch (Exception x) {
            Logger.error("Failed to initialize the SSLContext: " + x);
        }    	
    }
}