package dcraft.filevault.work.steps;

import dcraft.filestore.CollectionSourceStream;
import dcraft.filestore.FileCollection;
import dcraft.filestore.FileStore;
import dcraft.filestore.local.LocalStore;
import dcraft.filestore.local.LocalStoreFile;
import dcraft.filevault.DepositHub;
import dcraft.hub.ResourceHub;
import dcraft.hub.app.ApplicationHub;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
import dcraft.hub.resource.KeyRingResource;
import dcraft.log.Logger;
import dcraft.stream.StreamWork;
import dcraft.stream.file.JoinStream;
import dcraft.stream.file.PgpDecryptStream;
import dcraft.stream.file.PgpVerifyStream;
import dcraft.stream.file.UngzipStream;
import dcraft.stream.file.UntarStream;
import dcraft.struct.RecordStruct;
import dcraft.task.ChainWork;
import dcraft.task.TaskContext;
import dcraft.util.StringUtil;

public class ExpandDepositWork extends ChainWork {
	static public ExpandDepositWork of(String nodeid, String depositid, LocalStoreFile destination, RecordStruct manifest) {
		ExpandDepositWork work = new ExpandDepositWork();
		work.manifest = manifest;
		work.nodeid = nodeid;
		work.depositid = depositid;
		work.destination = destination;
		return  work;
	}
	
	protected String nodeid = null;
	protected String depositid = null;
	protected RecordStruct manifest = null;
	protected LocalStoreFile destination = null;
	
	@Override
	protected void init(TaskContext taskctx) throws OperatingContextException {
		if (! "Deposit".equals(manifest.getFieldAsString("Type"))) {
			Logger.error("Unknown deposit type: " + manifest.getFieldAsString("Type"));
			taskctx.returnEmpty();
			return;
		}
		
		int cnt = (int) manifest.getFieldAsInteger("SplitCount", 0);
		
		if (cnt < 0) {
			Logger.error("Invalid SplitCount: " + cnt);
			taskctx.returnEmpty();
			return;
		}
		
		LocalStore nodeDepositStore = LocalStore.of(ApplicationHub.getDeploymentPath().resolve("nodes/" + ExpandDepositWork.this.nodeid + "/deposits"));
		
		KeyRingResource keyring = ResourceHub.getResources().getKeyRing();
		
		FileCollection finalfiles = new FileCollection();
		
		LocalStoreFile chkfile = nodeDepositStore.resolvePathToStore("/files/" + this.depositid + ".sig");
		
		for (int i = 1; i <= cnt; i++) {
			String fname = "/files/" + this.depositid + ".tgzp-" + StringUtil.leftPad(i + "", 4, '0');
			finalfiles.withFiles(nodeDepositStore.resolvePathToStore(fname));
		}
		
		this.then(
				StreamWork.of(
						CollectionSourceStream.of(finalfiles),
						new JoinStream(),
						new PgpVerifyStream()
								.withKeyResource(keyring)
								.withSignatureFile(chkfile.getLocalPath()),
						new PgpDecryptStream()
								.withKeyResource(keyring)
								.withPassword(keyring.getPassphrase()),
						new UngzipStream(),
						new UntarStream(),
						this.destination.allocStreamDest()
				)
		);
	}
}
