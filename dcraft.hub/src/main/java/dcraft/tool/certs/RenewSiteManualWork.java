package dcraft.tool.certs;

import com.jcraft.jsch.Session;
import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSchException;
import dcraft.tool.release.ServerHelper;
import dcraft.hub.ResourceHub;
import dcraft.hub.app.ApplicationHub;
import dcraft.hub.op.OperatingContextException;
import dcraft.log.Logger;
import dcraft.task.StateWork;
import dcraft.task.StateWorkStep;
import dcraft.task.TaskContext;
import dcraft.tenant.Site;
import dcraft.util.StringUtil;
import org.shredzone.acme4j.*;
import org.shredzone.acme4j.exception.AcmeException;
import org.shredzone.acme4j.util.CSRBuilder;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyPair;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.util.List;

/*
 * Only run this on development machines, relies on git to distribute keys/certs
 */
public class RenewSiteManualWork extends StateWork {
	static public RenewSiteManualWork of(List<String> domains) {
		RenewSiteManualWork work = new RenewSiteManualWork();

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

	protected ServerHelper ssh = new ServerHelper();
	protected Session sshsession = null;
	protected ChannelSftp sftp = null;
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
		if (ApplicationHub.isProduction()) {
			Logger.error("RenewSiteManualWork not allowed on production, use RenewSiteAutoWork instead.");
			return this.done;
		}
		
		this.ssh = new ServerHelper();

		if (! this.ssh.init(ApplicationHub.getDeployment())) {
			Logger.error("Missing or incomplete matrix config");
			return this.done;
		}

		try {
			this.sshsession = ssh.openSession();
			this.sftp = (ChannelSftp) sshsession.openChannel("sftp");
			this.sftp.connect();
		}
		catch (JSchException x) {
			Logger.error("Sftp Error: " + x);
			return this.done;
		}

		Site siteinfo = trun.getSite();

		this.wwwpath = Paths.get("/dcserver/deploy-" + ApplicationHub.getDeployment() + "/tenants/" + siteinfo.getTenant().getAlias() + "/www");

		if (! siteinfo.isRoot())
			this.wwwpath = Paths.get("/dcserver/deploy-" + ApplicationHub.getDeployment() + "/tenants/" + siteinfo.getTenant().getAlias() + "/sites/" + siteinfo.getAlias() + "/www");

		this.configpath = siteinfo.resolvePath("config");

		return StateWorkStep.NEXT;
	}

	public StateWorkStep prepAcmeAccount(TaskContext trun) throws OperatingContextException {
		try {
			KeyPair userpair = CertUtil.loadOrCreateUserKeyPair();

			// Get the Account - if there is no account yet, create a new one.
			this.acct = CertUtil.findOrRegisterAccount(CertUtil.newSession(true), userpair, null);
		}
		catch (IOException x) {
			Logger.error("ACME key error: " + x);
			return this.done;
		}
		catch (AcmeException x) {
			Logger.error("ACME account error: " + x);
			return this.done;
		}

		return StateWorkStep.NEXT;
	}

	public StateWorkStep orderCert(TaskContext trun) throws OperatingContextException {
		try {
			// Order the certificate
			Order order = this.acct.newOrder().domains(this.domains).create();

			// Perform all required authorizations
			for (Authorization auth : order.getAuthorizations()) {
				CertUtil.authorize(auth, null, this.ssh, this.sftp, this.wwwpath);
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
				// Did the order fail?
				if (order.getStatus() == Status.INVALID) {
					throw new AcmeException("Order failed... Giving up.");
				}

				// Wait for a few seconds
				Thread.sleep(3000L);

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

			// Store away the keystore.
			try (FileOutputStream fos = new FileOutputStream(this.configpath.resolve("certs.jks").toFile())) {
				ks.store(fos, password);
			}
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

		Logger.info("Success - cert saved!");

		return StateWorkStep.NEXT;
	}

	public StateWorkStep reloadSiteCerts(TaskContext trun) throws OperatingContextException {
		Site siteinfo = trun.getSite();
		
		CertUtil.loadTierCerts(this.configpath, siteinfo.getTierResources());
		
		Logger.info("Site Certificates Reloaded");
		
		return StateWorkStep.NEXT;
	}

	public StateWorkStep done(TaskContext trun) throws OperatingContextException {
		if ((this.sftp != null) && this.sftp.isConnected()) {
			this.sftp.exit();
			this.sftp = null;
		}

		if ((this.sshsession != null) && this.sshsession.isConnected()) {
			this.sshsession.disconnect();
			this.sshsession = null;
		}

		this.ssh = null;

		return StateWorkStep.STOP;
	}
}
