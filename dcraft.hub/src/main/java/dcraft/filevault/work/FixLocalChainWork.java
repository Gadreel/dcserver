package dcraft.filevault.work;

import dcraft.db.BasicRequestContext;
import dcraft.db.IConnectionManager;
import dcraft.db.fileindex.FileIndexAdapter;
import dcraft.filestore.FileStoreFile;
import dcraft.filestore.local.LocalStoreFile;
import dcraft.filevault.DepositHub;
import dcraft.filevault.Vault;
import dcraft.hub.ResourceHub;
import dcraft.hub.app.ApplicationHub;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
import dcraft.hub.op.OperationOutcome;
import dcraft.hub.op.OperationOutcomeEmpty;
import dcraft.hub.resource.KeyRingResource;
import dcraft.log.Logger;
import dcraft.struct.CompositeParser;
import dcraft.struct.CompositeStruct;
import dcraft.struct.RecordStruct;
import dcraft.struct.scalar.StringStruct;
import dcraft.task.StateWork;
import dcraft.task.StateWorkStep;
import dcraft.task.TaskContext;
import dcraft.tenant.Site;
import dcraft.tenant.Tenant;
import dcraft.tenant.TenantHub;
import dcraft.util.StringUtil;
import dcraft.util.chars.Utf8Encoder;
import dcraft.util.pgp.ClearsignUtil;
import org.bouncycastle.openpgp.PGPSecretKeyRing;

import java.io.ByteArrayInputStream;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

public class FixLocalChainWork extends StateWork {
	protected Deque<FileStoreFile> folders = new ArrayDeque<>();
	
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
		
		LocalStoreFile chainfile = DepositHub.DepositStore.resolvePathToStore("/chain/" + did + ".chain");
		
		if (! chainfile.exists())
			return this.done;
		
		this.depositid++;
		
		KeyRingResource keyring = ResourceHub.getResources().getKeyRing();
		
		PGPSecretKeyRing seclocalsign = keyring.findUserSecretKey(ApplicationHub.getNodeId()
				+ "-signer@" + ApplicationHub.getDeployment() + ".dc");
		
		StringStruct chainsig = StringStruct.ofEmpty();
		
		chainfile.readAllText(new OperationOutcome<String>() {
			@Override
			public void callback(String result) throws OperatingContextException {
				StringBuilder sb = new StringBuilder();
				
				ClearsignUtil.verifyFile(new ByteArrayInputStream(Utf8Encoder.encode(result)), keyring, sb, chainsig, null);
				
				trun.clearExitCode();
				
				System.out.print("i");
				
				ClearsignUtil.ClearSignResult csresult = ClearsignUtil.clearSignMessage(sb.toString(), keyring, seclocalsign, keyring.getPassphrase());
				
				chainfile.writeAllText(csresult.file, new OperationOutcomeEmpty() {
					@Override
					public void callback() throws OperatingContextException {
						System.out.print("o");
						
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
