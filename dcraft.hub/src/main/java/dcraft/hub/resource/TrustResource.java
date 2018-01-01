package dcraft.hub.resource;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class TrustResource extends ResourceBase {
	static public final String[] DEFAULT_CIPHERS = new String[] {
			// AES 256 GCM SHA 384
			"TLS_RSA_WITH_AES_256_GCM_SHA384",
			"TLS_ECDH_ECDSA_WITH_AES_256_GCM_SHA384",
			"TLS_ECDH_RSA_WITH_AES_256_GCM_SHA384",
			"TLS_ECDHE_ECDSA_WITH_AES_256_GCM_SHA384",
			"TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384",
			// AES 256 CBC SHA 384
			"TLS_ECDH_ECDSA_WITH_AES_256_CBC_SHA384",
			"TLS_ECDH_RSA_WITH_AES_256_CBC_SHA384",
			"TLS_ECDHE_ECDSA_WITH_AES_256_CBC_SHA384",
			"TLS_ECDHE_RSA_WITH_AES_256_CBC_SHA384",
			// AES 256 CBC SHA 256
			"TLS_RSA_WITH_AES_256_CBC_SHA256",
			// AES 128 GCM SHA 256
			"TLS_RSA_WITH_AES_128_GCM_SHA256",
			"TLS_ECDH_ECDSA_WITH_AES_128_GCM_SHA256",
			"TLS_ECDH_RSA_WITH_AES_128_GCM_SHA256",
			"TLS_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256",
			"TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256"
	};
	
	protected List<TrustEntry> trustedcerts = new ArrayList<>();
	protected List<SslEntry> sslcerts = new ArrayList<>();
	protected SslEntry defualtssl = null;
	protected String protocol = null;
	protected String algorithm = null;
	protected List<String> ciphers = new ArrayList<>();
	
	public TrustResource() {
		this.setName("Trust");
	}
	
	public TrustResource withTrust(TrustEntry v) {
		this.trustedcerts.add(v);
		return this;
	}
	
	public TrustResource withSsl(SslEntry v, boolean defualtssl) {
		this.sslcerts.add(v);
		
		if (defualtssl)
			this.defualtssl = v;
		
		return this;
	}
	
	public TrustResource getParentResource() {
		if (this.tier == null)
			return null;
		
		ResourceTier pt = this.tier.getParent();
		
		if (pt != null)
			return pt.getTrust();
		
		return null;
	}
	
	public SslEntry lookupSsl(String hostname) {
		for (SslEntry entry : this.sslcerts) {
			if (entry.keynameMatch(hostname))
				return entry;
		}
		
		if (this.defualtssl != null)
			return this.defualtssl;
		
		TrustResource p = this.getParentResource();
		
		if (p != null)
			return p.lookupSsl(hostname);
		
		return null;
	}
	
	public String getAlgorithm() {
		if (this.algorithm != null)
			return this.algorithm;
		
		TrustResource p = this.getParentResource();
		
		if (p != null)
			return p.getAlgorithm();
		
		return "SunX509";
	}
	
	public String getProtocol() {
		if (this.protocol != null)
			return this.protocol;
		
		TrustResource p = this.getParentResource();
		
		if (p != null)
			return p.getProtocol();
		
		return "TLSv1.2";
	}
	
	public List<String> getCiphers() {
		if (this.ciphers.size() > 0)
			return this.ciphers;
		
		TrustResource p = this.getParentResource();
		
		if (p != null)
			return p.getCiphers();
		
		return Arrays.asList(TrustResource.DEFAULT_CIPHERS);
	}

	public List<TrustEntry> getAllTrustCerts() {
		TrustResource p = this.getParentResource();
		
		if (p == null)
			return new ArrayList<>(this.trustedcerts);
		
		List<TrustEntry> plist = p.getAllTrustCerts();
		
		plist.addAll(this.trustedcerts);
		
		return plist;
	}
	
}
