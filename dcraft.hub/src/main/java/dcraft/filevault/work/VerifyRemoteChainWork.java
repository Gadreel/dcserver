package dcraft.filevault.work;

import dcraft.filestore.CommonPath;
import dcraft.filestore.FileStore;
import dcraft.filestore.FileStoreFile;
import dcraft.filestore.local.LocalStoreFile;
import dcraft.filevault.DepositHub;
import dcraft.hub.ResourceHub;
import dcraft.hub.app.ApplicationHub;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationOutcome;
import dcraft.hub.op.OperationOutcomeEmpty;
import dcraft.hub.resource.KeyRingResource;
import dcraft.log.Logger;
import dcraft.struct.scalar.StringStruct;
import dcraft.task.StateWork;
import dcraft.task.StateWorkStep;
import dcraft.task.TaskContext;
import dcraft.util.StringUtil;
import dcraft.util.chars.Utf8Encoder;
import dcraft.util.pgp.ClearsignUtil;
import org.bouncycastle.openpgp.PGPSecretKeyRing;

import java.io.ByteArrayInputStream;
import java.util.ArrayDeque;
import java.util.Deque;

public class VerifyRemoteChainWork extends StateWork {
	protected long depositid = 0;  // "000000000000000";  15
	protected FileStore depositstore = null;
	
	protected StateWorkStep process = null;
	protected StateWorkStep done = null;
	
	@Override
	public void prepSteps(TaskContext trun) throws OperatingContextException {
		this.depositstore = DepositHub.getCloudStore(null, null);
		
		if (this.depositstore == null) {
			Logger.error("Unable to access cloud file store");
			return;
		}
		
		this
				.withStep(process = StateWorkStep.of("Process current deposit", this::process))
				.withStep(done = StateWorkStep.of("Done", this::done));
	}
	
	public StateWorkStep process(TaskContext trun) throws OperatingContextException {
		String did = StringUtil.leftPad(depositid + "", 15, '0');
		
		depositstore.getFileDetail(CommonPath.from("/deposits/" + ApplicationHub.getNodeId() + "/chain/" + did + ".chain"), new OperationOutcome<FileStoreFile>() {
			@Override
			public void callback(FileStoreFile cfile) throws OperatingContextException {
				
				if ((cfile == null) || ! cfile.exists()) {
					VerifyRemoteChainWork.this.transition(trun, VerifyRemoteChainWork.this.done);
					return;
				}
				
				Logger.info("Verify: " + cfile.getName());
				
				VerifyRemoteChainWork.this.depositid++;
				
				KeyRingResource keyring = ResourceHub.getResources().getKeyRing();
				
				StringStruct chainsig = StringStruct.ofEmpty();
				
				cfile.readAllText(new OperationOutcome<String>() {
					@Override
					public void callback(String result) throws OperatingContextException {
						StringBuilder sb = new StringBuilder();
						
						ClearsignUtil.verifyFile(new ByteArrayInputStream(Utf8Encoder.encode(result)), keyring, sb, chainsig);
						
						trun.clearExitCode();
						
						trun.resume();	// try next folder
					}
				});
			}
		});
		
		return StateWorkStep.WAIT;
	}
	
	public StateWorkStep done(TaskContext trun) throws OperatingContextException {
		return StateWorkStep.NEXT;
	}
}
