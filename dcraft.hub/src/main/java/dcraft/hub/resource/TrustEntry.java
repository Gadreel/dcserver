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

import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.concurrent.CopyOnWriteArraySet;

import javax.net.ssl.X509TrustManager;

import dcraft.util.KeyUtil;
import dcraft.util.StringUtil;
import dcraft.xml.XElement;

public class TrustEntry implements X509TrustManager {
	static public TrustEntry trust() {
		return new TrustEntry();
	}
	
	protected CopyOnWriteArraySet<String> trustedThumbs = new CopyOnWriteArraySet<>();
	
	public TrustEntry withThumb(String print) {
		if (StringUtil.isNotEmpty(print))
			this.trustedThumbs.add(print.toLowerCase().replace(":", ""));
		
		return this;
	}
	
    @Override
    public X509Certificate[] getAcceptedIssuers() {
        return new X509Certificate[] { };
    }
    
    @Override
    public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
		if (chain.length == 0)
        	throw new CertificateException("MISSING CLIENT CERTIFICATE");
		
		chain[0].checkValidity();
		
		/* TODO need to verify cert with master
		X509Certificate parent = ...;
		X509Certificate certToVerify = ...;
		certToVerify.verify(parent.getPublicKey());
		*/
		
    	String thumbprint = KeyUtil.getCertThumbprint(chain[0]);  
    	String subject = chain[0].getSubjectDN().toString();
    	
    	if (thumbprint == null)
        	throw new CertificateException("BAD CLIENT CERTIFICATE - CANNOT COMPUTE THUMBPRINT: " + subject);
    	
    	if (!this.trustedThumbs.contains(thumbprint)) {
            System.err.println("UNTRUSTED CLIENT CERTIFICATE: " + subject + " - thumbprint: " + thumbprint);
        	throw new CertificateException("UNTRUSTED CLIENT CERTIFICATE: " + subject);
    	}
    	
        System.err.println("TRUSTED CLIENT CERTIFICATE: " + subject + " - thumbprint: " + thumbprint);
    }

    @Override
    public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
		if (chain.length == 0)
        	throw new CertificateException("MISSING SERVER CERTIFICATE");
		
		chain[0].checkValidity();
		
		/* TODO need to verify cert with master
		X509Certificate parent = ...;
		X509Certificate certToVerify = ...;
		certToVerify.verify(parent.getPublicKey());
		*/
    	
    	String subject = chain[0].getSubjectDN().toString();
    	String thumbprint = KeyUtil.getCertThumbprint(chain[0]);  
    	
    	if (thumbprint == null)
        	throw new CertificateException("BAD SERVER CERTIFICATE - CANNOT COMPUTE THUMBPRINT: " + subject);
    	
    	if (!this.trustedThumbs.contains(thumbprint)) {
            System.err.println("UNTRUSTED SERVER CERTIFICATE: " + subject + " - thumbprint: " + thumbprint);
        	throw new CertificateException("UNTRUSTED SERVER CERTIFICATE: " + subject);
    	}
    	
        System.err.println("TRUSTED SERVER CERTIFICATE: " + subject + " - thumbprint: " + thumbprint);
    }
}
