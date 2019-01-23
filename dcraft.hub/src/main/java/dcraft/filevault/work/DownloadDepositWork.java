package dcraft.filevault.work;

import dcraft.filestore.CollectionSourceStream;
import dcraft.filestore.CommonPath;
import dcraft.filestore.FileCollection;
import dcraft.filestore.FileDescriptor;
import dcraft.filestore.FileStore;
import dcraft.filestore.FileStoreFile;
import dcraft.filestore.local.LocalStore;
import dcraft.filestore.local.LocalStoreFile;
import dcraft.filevault.DepositHub;
import dcraft.filevault.Transaction;
import dcraft.hub.ResourceHub;
import dcraft.hub.app.ApplicationHub;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
import dcraft.hub.op.OperationOutcome;
import dcraft.hub.op.OperationOutcomeEmpty;
import dcraft.hub.resource.KeyRingResource;
import dcraft.log.Logger;
import dcraft.stream.ReturnOption;
import dcraft.stream.StreamFragment;
import dcraft.stream.StreamWork;
import dcraft.stream.file.FileSlice;
import dcraft.stream.file.JoinStream;
import dcraft.stream.file.PgpDecryptStream;
import dcraft.stream.file.PgpVerifyStream;
import dcraft.stream.file.TransformFileStream;
import dcraft.stream.file.UngzipStream;
import dcraft.stream.file.UntarStream;
import dcraft.struct.CompositeParser;
import dcraft.struct.CompositeStruct;
import dcraft.struct.RecordStruct;
import dcraft.struct.scalar.StringStruct;
import dcraft.task.ChainWork;
import dcraft.task.ControlWork;
import dcraft.task.IWork;
import dcraft.task.StateWork;
import dcraft.task.StateWorkStep;
import dcraft.task.TaskContext;
import dcraft.util.IOUtil;
import dcraft.util.StringUtil;
import dcraft.util.chars.Utf8Encoder;
import dcraft.util.pgp.ClearsignUtil;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class DownloadDepositWork extends StateWork {
	static public DownloadDepositWork of(String depositid, String nodeid, RecordStruct manifest) {
		DownloadDepositWork work = new DownloadDepositWork();
		work.did = depositid;
		work.manifest = manifest;
		work.prodnodeid = nodeid;
		return work;
	}
	
	protected String did = null;
	protected RecordStruct manifest = null;
	protected String prodnodeid = null;
	
	protected FileStore remotedepositstore = null;		// remote
	protected LocalStore localdepositstore = null;
	
	protected int splitcurr = 1;
	protected int splitmax = 0;
	
	protected StateWorkStep downloadSig = null;
	protected StateWorkStep downloadPart = null;
	protected StateWorkStep nextPart = null;
	protected StateWorkStep verifyDeposit = null;
	protected StateWorkStep done = null;
	
	@Override
	public void prepSteps(TaskContext trun) throws OperatingContextException {
		this.localdepositstore = LocalStore.of(ApplicationHub.getDeploymentPath().resolve("nodes/" + prodnodeid + "/deposits"));
		
		this.remotedepositstore = DepositHub.getCloudStore(null, "Production");
		
		if (this.remotedepositstore == null) {
			Logger.error("Unable to access cloud file store");
			return;
		}
		
		if (! "Deposit".equals(this.manifest.getFieldAsString("Type"))) {
			Logger.error("Unknown deposit type: " + this.manifest.getFieldAsString("Type"));
			return;
		}
		
		
		this
				.withStep(downloadSig = StateWorkStep.of("Get chain file", this::downloadSig))
				.withStep(downloadPart = StateWorkStep.of("Verify chain file", this::downloadPart))
				.withStep(nextPart = StateWorkStep.of("Process current deposit file", this::nextPart))
				.withStep(verifyDeposit = StateWorkStep.of("Process current deposit file", this::verifyDeposit))
				.withStep(done = StateWorkStep.of("Done", this::done));
	}
	
	public StateWorkStep downloadSig(TaskContext trun) throws OperatingContextException {
		this.splitmax = (int) this.manifest.getFieldAsInteger("SplitCount", 0);
		
		if (this.splitmax < 0) {
			Logger.error("Invalid SplitCount: " + this.splitmax);
			return this.done;
		}
		
		if (this.splitmax == 0) {
			return this.done;
		}
		
		Logger.info("Downloading sig: " + did);
		
		LocalStoreFile lfile = localdepositstore.resolvePathToStore("/files/" + did + ".sig");
		
		// if local skip download
		if (lfile.exists())
			return this.downloadPart;

		StreamFragment fragment =remotedepositstore.fileReference(CommonPath.from("/deposits/" + this.prodnodeid
				+ "/files/" + did + ".sig")).allocStreamSrc();

		fragment.withAppend(lfile.allocStreamDest());

		this.chainThen(
				trun,
				StreamWork.of(fragment),
				this.downloadPart
		);
		
		return StateWorkStep.WAIT;
	}
	
	public StateWorkStep downloadPart(TaskContext trun) throws OperatingContextException {
		Logger.info("Downloading part: " + did + " : " + this.splitcurr);
		
		String fname = did + ".tgzp-" + StringUtil.leftPad(this.splitcurr + "", 4, '0');
		
		LocalStoreFile lfile = localdepositstore.resolvePathToStore("/files/" + fname);
		
		// if local skip download
		if (lfile.exists())
			return this.nextPart;

		StreamFragment fragment = remotedepositstore.fileReference(CommonPath.from("/deposits/" + this.prodnodeid
				+ "/files/" + fname))
				.allocStreamSrc();

		fragment.withAppend(lfile.allocStreamDest());

		this.chainThen(
				trun,
				StreamWork.of(fragment),
				this.nextPart
		);
		
		return StateWorkStep.WAIT;
	}
	
	public StateWorkStep nextPart(TaskContext trun) throws OperatingContextException {
		if (this.splitcurr == this.splitmax)
			return this.verifyDeposit;
		
		this.splitcurr++;
		
		return this.downloadPart;
	}
	
	public StateWorkStep verifyDeposit(TaskContext trun) throws OperatingContextException {
		// TODO have an optional verify here, should also verify in Expand so won't always want to here
		
		return StateWorkStep.NEXT;
	}
	
	public StateWorkStep done(TaskContext trun) throws OperatingContextException {
		Logger.info("Deposit files downloaded: " + did);
		
		return StateWorkStep.STOP;
	}
}
