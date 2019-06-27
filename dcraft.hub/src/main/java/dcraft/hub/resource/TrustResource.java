package dcraft.hub.resource;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class TrustResource extends ResourceBase {
	static public final String[] DEFAULT_PROTOCOLS = new String[] { "TLSv1.2", "TLSv1.3" };
	static public final String[] DEFAULT_CIPHERS = new String[] {
			// TLS 1.3
			"TLS_AES_128_GCM_SHA256",
			"TLS_AES_256_GCM_SHA384",
			"TLS_CHACHA20_POLY1305_SHA256",
			// TLS 1.2 preferred
			"TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256",
			"TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA",

			// TLS 1.2 DSA
			//"TLS_ECDHE_ECDSA_WITH_AES_256_GCM_SHA384",
			//"TLS_ECDHE_ECDSA_WITH_AES_256_CBC_SHA",
			//"TLS_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256",
			//"TLS_ECDHE_ECDSA_WITH_AES_128_CBC_SHA",

			// TLS 1.2 other
			"TLS_RSA_WITH_AES_256_GCM_SHA384",
			"TLS_RSA_WITH_AES_128_GCM_SHA256"
			//"TLS_RSA_WITH_AES_128_CBC_SHA"
	};

	/*
	TLS 1.2
 xc02c   ECDHE-ECDSA-AES256-GCM-SHA384     ECDH 253   AESGCM      256      TLS_ECDHE_ECDSA_WITH_AES_256_GCM_SHA384
 xc00a   ECDHE-ECDSA-AES256-SHA            ECDH 253   AES         256      TLS_ECDHE_ECDSA_WITH_AES_256_CBC_SHA
 xc02b   ECDHE-ECDSA-AES128-GCM-SHA256     ECDH 253   AESGCM      128      TLS_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256
 xc009   ECDHE-ECDSA-AES128-SHA            ECDH 253   AES         128      TLS_ECDHE_ECDSA_WITH_AES_128_CBC_SHA
TLS 1.3
 x1302   TLS_AES_256_GCM_SHA384            ECDH 253   AESGCM      256      TLS_AES_256_GCM_SHA384
 x1303   TLS_CHACHA20_POLY1305_SHA256      ECDH 253   ChaCha20    256      TLS_CHACHA20_POLY1305_SHA256
 x1301   TLS_AES_128_GCM_SHA256            ECDH 253   AESGCM      128      TLS_AES_128_GCM_SHA256



	 */
	
	protected List<TrustEntry> trustedcerts = new ArrayList<>();
	protected List<SslEntry> sslcerts = new ArrayList<>();
	protected SslEntry defualtssl = null;
	protected List<String> protocols = new ArrayList<>();
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

	public TrustResource withProtocols(String... v) {
		for (int i = 0; i < v.length; i++)
			this.protocols.add(v[i]);

		return this;
	}

	public TrustResource withCiphers(String... v) {
		for (int i = 0; i < v.length; i++)
			this.ciphers.add(v[i]);

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

	public SslEntry matchSsl(String hostname) {
		for (SslEntry entry : this.sslcerts) {
			if (entry.keynameMatch(hostname))
				return entry;
		}

		TrustResource p = this.getParentResource();

		if (p != null)
			return p.matchSsl(hostname);

		return null;
	}

	public SslEntry matchTierSsl(String hostname) {
		for (SslEntry entry : this.sslcerts) {
			if (entry.keynameMatch(hostname))
				return entry;
		}

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
	
	public List<String> getProtocols() {
		if (this.protocols.size() > 0)
			return this.protocols;

		TrustResource p = this.getParentResource();
		
		if (p != null)
			return p.getProtocols();
		
		return Arrays.asList(TrustResource.DEFAULT_PROTOCOLS);
	}
	
	public List<String> getCiphers() {
		if (this.ciphers.size() > 0)
			return this.ciphers;
		
		TrustResource p = this.getParentResource();
		
		if (p != null)
			return p.getCiphers();
		
		return Arrays.asList(TrustResource.DEFAULT_CIPHERS);
	}

	public List<SslEntry> getTierSslCerts() {
		return new ArrayList<>(this.sslcerts);
	}

	public List<TrustEntry> getTierTrustCerts() {
		return new ArrayList<>(this.trustedcerts);
	}

	public List<TrustEntry> getAllTrustCerts() {
		TrustResource p = this.getParentResource();
		
		if (p == null)
			return new ArrayList<>(this.trustedcerts);
		
		List<TrustEntry> plist = p.getAllTrustCerts();
		
		plist.addAll(this.trustedcerts);
		
		return plist;
	}

	public void clearSslCerts() {
		this.sslcerts = new ArrayList<>();
	}

	public void switchSslCerts(List<SslEntry> newssls) {
		this.sslcerts = newssls;
	}

	@Override
	public void cleanup() {
		// TODO what should be removed?
		super.cleanup();
	}
}
