package dcraft.hub.resource;

import dcraft.hub.app.ApplicationHub;
import dcraft.util.StringUtil;
import dcraft.util.pgp.KeyRingCollection;
import dcraft.xml.XElement;
import org.bouncycastle.openpgp.*;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class KeyRingResource extends ResourceBase {
	protected List<KeyRingCollection> keys = new ArrayList<>();
	
	public KeyRingResource() {
		this.setName("KeyRing");
	}
	
	public KeyRingResource getParentResource() {
		if (this.tier == null)
			return null;
		
		ResourceTier pt = this.tier.getParent();
		
		if (pt != null)
			return pt.getKeyRing();
		
		return null;
	}
	
	public KeyRingResource withKeys(KeyRingCollection... keys) {
		for (KeyRingCollection key : keys)
			this.keys.add(key);
		
		return this;
	}
	
	public PGPPublicKeyRing findUserPublicKey(String userid) {
		for (int i = this.keys.size() - 1; i >= 0; i--) {
			PGPPublicKeyRing pub = this.keys.get(i).findUserPublicKey(userid);
			
			if (pub != null)
				return pub;
		}
		
		KeyRingResource parent = this.getParentResource();
		
		if (parent != null)
			return parent.findUserPublicKey(userid);
		
		return null;
	}
	
	public PGPSecretKeyRing findUserSecretKey(String userid) {
		for (int i = this.keys.size() - 1; i >= 0; i--) {
			PGPSecretKeyRing pub = this.keys.get(i).findUserSecretKey(userid);
			
			if (pub != null)
				return pub;
		}
		
		KeyRingResource parent = this.getParentResource();
		
		if (parent != null)
			return parent.findUserSecretKey(userid);
		
		return null;
	}
	
	public PGPPrivateKey findSecretKey(long keyid, char[] pass) throws PGPException {
		for (int i = this.keys.size() - 1; i >= 0; i--) {
			PGPPrivateKey pub = this.keys.get(i).findSecretKey(keyid, pass);
			
			if (pub != null)
				return pub;
		}
		
		KeyRingResource parent = this.getParentResource();
		
		if (parent != null)
			return parent.findSecretKey(keyid, pass);
		
		return null;
	}
	
	public PGPPublicKey findPublicKey(long keyid) throws PGPException {
		for (int i = this.keys.size() - 1; i >= 0; i--) {
			PGPPublicKey pub = this.keys.get(i).findPublicKey(keyid);
			
			if (pub != null)
				return pub;
		}
		
		KeyRingResource parent = this.getParentResource();
		
		if (parent != null)
			return parent.findPublicKey(keyid);
		
		return null;
	}
	
	// all keyrings should use the same phrase because during decrypt / verify we will not know which ring to use
	public char[] getPassphrase() {
		XElement krel = this.getTier().getConfig().getTag("Keyrings");
		
		if (krel == null)
			return null;
		
		String passpharse = krel.getAttribute("PlainPassword");
		String encpassphrase = krel.getAttribute("Password");
		
		if (StringUtil.isNotEmpty(encpassphrase))
			// try to decrypt, if we succeed then we use it - if we don't then use plain text password
			passpharse = ApplicationHub.getClock().getObfuscator().decryptHexToString(encpassphrase);
		
		if (StringUtil.isEmpty(passpharse))
			return null;
			
		return passpharse.toCharArray();
	}
}
