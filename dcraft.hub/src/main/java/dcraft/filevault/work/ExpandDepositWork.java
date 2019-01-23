package dcraft.filevault.work;

import dcraft.filestore.CollectionSourceStream;
import dcraft.filestore.CommonPath;
import dcraft.filestore.FileCollection;
import dcraft.filestore.FileStore;
import dcraft.filestore.local.LocalStore;
import dcraft.filestore.local.LocalStoreFile;
import dcraft.filevault.DepositHub;
import dcraft.hub.ResourceHub;
import dcraft.hub.app.ApplicationHub;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.resource.KeyRingResource;
import dcraft.log.Logger;
import dcraft.stream.StreamFragment;
import dcraft.stream.StreamWork;
import dcraft.stream.file.JoinStream;
import dcraft.stream.file.PgpDecryptStream;
import dcraft.stream.file.PgpVerifyStream;
import dcraft.stream.file.UngzipStream;
import dcraft.stream.file.UntarStream;
import dcraft.struct.RecordStruct;
import dcraft.task.StateWork;
import dcraft.task.StateWorkStep;
import dcraft.task.TaskContext;
import dcraft.util.StringUtil;

public class ExpandDepositWork extends StateWork {
	static public ExpandDepositWork of(String depositid, String nodeid, RecordStruct manifest, LocalStoreFile destination) {
		ExpandDepositWork work = new ExpandDepositWork();
		work.did = depositid;
		work.manifest = manifest;
		work.prodnodeid = nodeid;
		work.destination = destination;
		return work;
	}
	
	protected String did = null;
	protected RecordStruct manifest = null;
	protected String prodnodeid = null;
	protected LocalStoreFile destination = null;
	
	protected LocalStore localdepositstore = null;
	
	protected StateWorkStep expand = null;
	protected StateWorkStep done = null;
	
	@Override
	public void prepSteps(TaskContext trun) throws OperatingContextException {
		this.localdepositstore = LocalStore.of(ApplicationHub.getDeploymentPath().resolve("nodes/" + prodnodeid + "/deposits"));
		
		if (! "Deposit".equals(this.manifest.getFieldAsString("Type"))) {
			Logger.error("Unknown deposit type: " + this.manifest.getFieldAsString("Type"));
			return;
		}
		
		
		this
				.withStep(expand = StateWorkStep.of("Expand deposit", this::expand))
				.withStep(done = StateWorkStep.of("Done", this::done));
	}
	
	public StateWorkStep expand(TaskContext trun) throws OperatingContextException {
		StreamFragment source = ExpandDepositWork.depositSource(this.did, this.localdepositstore, this.manifest);

		if (source == null) {
			return this.done;
		}

		source.withAppend(this.destination.allocStreamDest());

		this.chainThen(
				trun,
				StreamWork.of(source),
				this.done
		);
		
		return StateWorkStep.WAIT;
	}
	
	public StateWorkStep done(TaskContext trun) throws OperatingContextException {
		Logger.info("Deposit files expanded: " + did);
		
		return StateWorkStep.STOP;
	}

	static public StreamFragment depositSource(String did, LocalStore localdepositstore, RecordStruct manifest) {
		int cnt = (int) manifest.getFieldAsInteger("SplitCount", 0);

		if (cnt < 0) {
			Logger.error("Invalid SplitCount: " + cnt);
			return null;
		}

		if (cnt == 0) {
			return null;
		}

		Logger.info("Expanding deposit: " + did);

		KeyRingResource keyring = ResourceHub.getResources().getKeyRing();

		FileCollection finalfiles = new FileCollection();

		LocalStoreFile chkfile = localdepositstore.resolvePathToStore("/files/" + did + ".sig");

		for (int i = 1; i <= cnt; i++) {
			String fname = "/files/" + did + ".tgzp-" + StringUtil.leftPad(i + "", 4, '0');
			finalfiles.withFiles(localdepositstore.resolvePathToStore(fname));
		}

		return StreamFragment.of(
			CollectionSourceStream.of(finalfiles),
				new JoinStream(),
				new PgpVerifyStream()
						.withKeyResource(keyring)
						.withSignatureFile(chkfile.getLocalPath()),
				new PgpDecryptStream()
						.withKeyResource(keyring)
						.withPassword(keyring.getPassphrase()),
				new UngzipStream(),
				new UntarStream()
		);
	}
}
