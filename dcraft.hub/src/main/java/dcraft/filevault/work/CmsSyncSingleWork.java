package dcraft.filevault.work;

import dcraft.filestore.CommonPath;
import dcraft.filestore.FileStore;
import dcraft.filestore.FileStoreFile;
import dcraft.filestore.local.LocalStore;
import dcraft.filestore.local.LocalStoreFile;
import dcraft.filevault.DepositHub;
import dcraft.filevault.ManifestTransaction;
import dcraft.filevault.Vault;
import dcraft.hub.ResourceHub;
import dcraft.hub.app.ApplicationHub;
import dcraft.hub.op.*;
import dcraft.hub.resource.KeyRingResource;
import dcraft.log.Logger;
import dcraft.struct.RecordStruct;
import dcraft.struct.Struct;
import dcraft.struct.scalar.StringStruct;
import dcraft.task.*;
import dcraft.tenant.Site;
import dcraft.tenant.Tenant;
import dcraft.tenant.TenantHub;
import dcraft.util.IOUtil;
import dcraft.util.StringUtil;
import dcraft.util.chars.Utf8Encoder;
import dcraft.util.pgp.ClearsignUtil;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class CmsSyncSingleWork extends CmsSyncWork {
	static public CmsSyncSingleWork of (String nodeid, long depositid) {
		CmsSyncSingleWork work = new CmsSyncSingleWork();
		work.prodnodeid = nodeid;
		work.depositid = depositid;
		return work;
	}

	@Override
	public void prepSteps(TaskContext trun) throws OperatingContextException {
		this.localdepositstore = LocalStore.of(ApplicationHub.getDeploymentPath().resolve("nodes/" + prodnodeid + "/deposits"));
		
		this.remotedepositstore = DepositHub.getCloudStore(null, "Production");
		
		if (this.remotedepositstore == null) {
			Logger.error("Unable to access cloud file store");
			return;
		}

		this.formatId();
		
		this
				.withStep(verifychain = StateWorkStep.of("Verify chain file", this::verify))
				.withStep(review = StateWorkStep.of("Review, download current deposit file", this::reviewDownload))
				.withStep(expand = StateWorkStep.of("Expand current deposit file", this::expand))
				.withStep(commit = StateWorkStep.of("Commit current deposit file", this::commit))
				.withStep(next = StateWorkStep.of("Increment deposit number", this::next))
				.withStep(done = StateWorkStep.of("Done", this::done));
	}
	
	public void formatId() {
		this.did = StringUtil.leftPad(depositid + "", 15, '0');
	}

	public StateWorkStep next(TaskContext trun) throws OperatingContextException {
		return this.done;
	}
	
	public StateWorkStep done(TaskContext trun) throws OperatingContextException {
		Logger.info("Deposit processed");

		return StateWorkStep.NEXT;
	}
}
