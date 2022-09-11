package dcraft.tool.certs;

import dcraft.filestore.CommonPath;
import dcraft.filestore.mem.MemoryStoreFile;
import dcraft.filevault.VaultUtil;
import dcraft.hub.ResourceHub;
import dcraft.hub.app.ApplicationHub;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationOutcomeStruct;
import dcraft.log.Logger;
import dcraft.struct.BaseStruct;
import dcraft.task.StateWork;
import dcraft.task.StateWorkStep;
import dcraft.task.TaskContext;
import dcraft.tenant.Site;
import dcraft.util.Memory;
import dcraft.util.StringUtil;
import dcraft.util.io.OutputWrapper;
import org.shredzone.acme4j.Account;
import org.shredzone.acme4j.Authorization;
import org.shredzone.acme4j.Certificate;
import org.shredzone.acme4j.Order;
import org.shredzone.acme4j.Status;
import org.shredzone.acme4j.exception.AcmeException;
import org.shredzone.acme4j.util.CSRBuilder;

import java.io.IOException;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.util.List;

public class RenewSiteAutoWork extends StateWork {
	static public RenewSiteAutoWork of(List<String> domains) {
		RenewSiteAutoWork work = new RenewSiteAutoWork();

		work.domains = domains;

		return work;
	}
	
	protected StateWorkStep init = null;
	protected StateWorkStep acme = null;
	protected StateWorkStep order = null;
	protected StateWorkStep write = null;
	protected StateWorkStep reload = null;
	protected StateWorkStep done = null;

	protected List<String> domains = null;
	
	protected Path wwwpath = null;
	protected Path configpath = null;
	protected Account acct = null;
	protected KeyPair domainKeyPair = null;
	protected Certificate certificate = null;
	
	@Override
	public void prepSteps(TaskContext trun) throws OperatingContextException {
		this.withSteps(
				init = StateWorkStep.of("Init Site SSL renewal", this::init),
				acme = StateWorkStep.of("Prep ACME account", this::prepAcmeAccount),
				order = StateWorkStep.of("Order cert", this::orderCert),
				write = StateWorkStep.of("Write cert", this::writeCert),
				reload = StateWorkStep.of("Reload site certs", this::reloadSiteCerts),
				done = StateWorkStep.of("Done Site SSL renewal", this::done)
		);
	}
	
	public StateWorkStep init(TaskContext trun) throws OperatingContextException {
		if (! ApplicationHub.isProduction()) {
			Logger.error("RenewSiteAutoWork not allowed on development, use RenewSiteManualWork instead.");
			return this.done;
		}
		
		Site siteinfo = trun.getSite();
		
		this.wwwpath = siteinfo.resolvePath("www");
		this.configpath = siteinfo.resolvePath("config");
		
		return StateWorkStep.NEXT;
	}
	
	public StateWorkStep prepAcmeAccount(TaskContext trun) throws OperatingContextException {
		trun.touch();

		try {
			KeyPair userpair = CertUtil.loadUserKeyPair();
			
			if (userpair == null) {
				Logger.error("Missing ACME account key pair.");
				return this.done;
			}
			
			// Get the Account - if there is no account yet, create a new one.
			this.acct = CertUtil.checkAccount(CertUtil.newSession(true), userpair);
			
			if (this.acct == null) {
				Logger.error("Missing ACME account.");
				return this.done;
			}
		}
		catch (IOException x) {
			Logger.error("ACME key error: " + x);
			return this.done;
		}
		
		return StateWorkStep.NEXT;
	}
	
	public StateWorkStep orderCert(TaskContext trun) throws OperatingContextException {
		trun.touch();

		try {
			// Order the certificate
			Order order = this.acct.newOrder().domains(this.domains).create();
			
			// Perform all required authorizations
			for (Authorization auth : order.getAuthorizations()) {
				if (! CertUtil.authorize(auth, null, this.wwwpath)) {
					return this.done;
				}
			}
			
			// Generate a CSR for all of the domains, and sign it with the domain key pair.
			CSRBuilder csrb = new CSRBuilder();
			csrb.addDomains(domains);
			
			this.domainKeyPair = CertUtil.loadOrCreateDomainKeyPair(this.configpath);
			
			csrb.sign(this.domainKeyPair);
			
			//Path domaincsr = siteconfig.resolve("certs.csr");
			
			// Write the CSR to a file, for later use.
			//try (Writer out = new FileWriter(domaincsr.toFile())) {
			//	csrb.write(out);
			//}
			
			// Order the certificate
			order.execute(csrb.getEncoded());
			
			// Wait for the order to complete
			int attempts = 10;
			
			while ((order.getStatus() != Status.VALID) && (attempts-- > 0)) {
				trun.touch();

				// Did the order fail?
				if (order.getStatus() == Status.INVALID) {
					throw new AcmeException("Order failed... Giving up.");
				}
				
				// Wait for a few seconds
				Thread.sleep(2000L);
				
				// Then update the status
				order.update();
			}
			
			// Get the certificate
			this.certificate = order.getCertificate();
			
			Logger.info("Success! The certificate for domains " + StringUtil.join(this.domains, ", ") + " has been generated!");
			Logger.info("Certificate URL: " + this.certificate.getLocation());
		}
		catch (AcmeException x) {
			Logger.error("ACME cert order error: " + x);
			return this.done;
		}
		catch (IOException x) {
			Logger.error("Domain key error: " + x);
			return this.done;
		}
		catch (InterruptedException x) {
			Logger.error("Order check Interrupted: " + x);
			return this.done;
		}
		
		return StateWorkStep.NEXT;
	}
	
	public StateWorkStep writeCert(TaskContext trun) throws OperatingContextException {
		trun.touch();

		try {
			
			//Path domainchain = siteconfig.resolve("certs.chain");
			
			// Write a combined file containing the certificate and chain.
			//try (FileWriter fw = new FileWriter(domainchain.toFile())) {
			//	certificate.writeCertificate(fw);
			//}
			
			// start keystore
			KeyStore ks = KeyStore.getInstance("JKS");
			
			char[] password = ResourceHub.getResources().getKeyRing().getPassphrase();
			
			ks.load(null, password);
			
			java.security.cert.Certificate[] certificates = new java.security.cert.Certificate[this.certificate.getCertificateChain().size()];
			certificates = this.certificate.getCertificateChain().toArray(certificates);
			
			ks.setKeyEntry(trun.getSite().getAlias(), this.domainKeyPair.getPrivate(), password, certificates);
			
			// store in Vault
			
			Memory memory = new Memory();
			
			// Store away the keystore.
			try (OutputWrapper fos = new OutputWrapper(memory)) {
				ks.store(fos, password);
			}
			
			memory.setPosition(0);
			
			CommonPath cpath = CommonPath.from("/config/certs.jks");
			
			MemoryStoreFile msource = MemoryStoreFile.of(cpath)
					.with(memory);
			
			Logger.info("Writing certificate");
			
			VaultUtil.transfer("SiteFiles", msource, cpath, null, new OperationOutcomeStruct() {
				@Override
				public void callback(BaseStruct result) throws OperatingContextException {
					Logger.info("Success - cert saved!");
					
					RenewSiteAutoWork.this.transition(trun, reload);
				}
			});
			
			return StateWorkStep.WAIT;
		}
		catch (NoSuchAlgorithmException x) {
			Logger.error("Cert algorithm error: " + x);
			return this.done;
		}
		catch (CertificateException x) {
			Logger.error("Cert structure error: " + x);
			return this.done;
		}
		catch (KeyStoreException x) {
			Logger.error("Cert keystore error: " + x);
			return this.done;
		}
		catch (IOException x) {
			Logger.error("Cert write error: " + x);
			return this.done;
		}
	}
	
	public StateWorkStep reloadSiteCerts(TaskContext trun) throws OperatingContextException {
		Site siteinfo = trun.getSite();
		
		CertUtil.loadTierCerts(this.configpath, siteinfo.getTierResources());
		
		Logger.info("Site Certificates Reloaded");
		
		return StateWorkStep.NEXT;
	}
	
	public StateWorkStep done(TaskContext trun) throws OperatingContextException {
		return StateWorkStep.STOP;
	}
}
