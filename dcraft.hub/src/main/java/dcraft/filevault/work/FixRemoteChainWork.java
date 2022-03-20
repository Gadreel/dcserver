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

public class FixRemoteChainWork extends StateWork {
	protected long depositid = 0;  // "000000000000000";  15
	
	protected StateWorkStep process = null;
	protected StateWorkStep done = null;
	
	@Override
	public void prepSteps(TaskContext trun) throws OperatingContextException {
		this
				.withStep(process = StateWorkStep.of("Index Site", this::process))
				.withStep(done = StateWorkStep.of("Done", this::done));
	}
	
	public StateWorkStep process(TaskContext trun) throws OperatingContextException {
		String did = StringUtil.leftPad(depositid + "", 15, '0');
		
		FileStore depositstore = DepositHub.getCloudStore(null, null);
		
		if (depositstore == null) {
			Logger.error("Unable to access cloud file store");
			return this.done;
		}
		
		depositstore.getFileDetail(CommonPath.from("/deposits/" + ApplicationHub.getNodeId() + "/chain/" + did + ".chain"), new OperationOutcome<FileStoreFile>() {
			@Override
			public void callback(FileStoreFile cfile) throws OperatingContextException {
				
				if ((cfile == null) || ! cfile.exists()) {
					FixRemoteChainWork.this.transition(trun, FixRemoteChainWork.this.done);
					return;
				}
				
				FixRemoteChainWork.this.depositid++;
				
				KeyRingResource keyring = ResourceHub.getResources().getKeyRing();
				
				PGPSecretKeyRing seclocalsign = keyring.findUserSecretKey(ApplicationHub.getNodeId()
						+ "-signer@" + ApplicationHub.getDeployment() + ".dc");
				
				StringStruct chainsig = StringStruct.ofEmpty();
				
				cfile.readAllText(new OperationOutcome<String>() {
					@Override
					public void callback(String result) throws OperatingContextException {
						StringBuilder sb = new StringBuilder();
						
						ClearsignUtil.verifyFile(new ByteArrayInputStream(Utf8Encoder.encode(result)), keyring, sb, chainsig, null);
						
						trun.clearExitCode();
						
						System.out.print("i");
						
						ClearsignUtil.ClearSignResult csresult = ClearsignUtil.clearSignMessage(sb.toString(), keyring, seclocalsign, keyring.getPassphrase());
						
						LocalStoreFile lfile = DepositHub.DepositStore.resolvePathToStore("/chain/" + did + ".chain");
						
						lfile.writeAllText(csresult.file, new OperationOutcomeEmpty() {
							@Override
							public void callback() throws OperatingContextException {
								System.out.print("o");
								
								cfile.writeAllText(csresult.file, new OperationOutcomeEmpty() {
									@Override
									public void callback() throws OperatingContextException {
										System.out.print("c");
										
										trun.resume();	// try next folder
									}
								});
							}
						});
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
