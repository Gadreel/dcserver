package dcraft.filevault.work.steps;

import dcraft.filestore.local.LocalStoreFile;
import dcraft.filevault.work.CmsSyncWork;
import dcraft.hub.ResourceHub;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
import dcraft.hub.op.OperationOutcome;
import dcraft.hub.resource.KeyRingResource;
import dcraft.log.Logger;
import dcraft.struct.CompositeParser;
import dcraft.struct.CompositeStruct;
import dcraft.struct.RecordStruct;
import dcraft.struct.scalar.StringStruct;
import dcraft.task.ChainWork;
import dcraft.task.IWork;
import dcraft.task.TaskContext;
import dcraft.util.FileUtil;
import dcraft.util.IOUtil;
import dcraft.util.chars.Utf8Encoder;
import dcraft.util.pgp.ClearsignUtil;

import java.io.ByteArrayInputStream;

public class CheckManifestWork implements IWork {
	static public CheckManifestWork of(String depositid, LocalStoreFile local) {
		CheckManifestWork work = new CheckManifestWork();
		work.depositid = depositid;
		work.local = local;
		return work;
	}
	
	protected String depositid = null;
	protected LocalStoreFile local = null;
	protected RecordStruct manifest = null;
	
	public RecordStruct getManifest() {
		return this.manifest;
	}
	
	@Override
	public void run(TaskContext taskctx) throws OperatingContextException {
		this.local.refreshProps();

		KeyRingResource keyring = ResourceHub.getResources().getKeyRing();
		
		StringStruct chainsig = StringStruct.ofEmpty();
		
		this.local.readAllText(new OperationOutcome<String>() {
			@Override
			public void callback(String result) throws OperatingContextException {
				StringBuilder sb = new StringBuilder();
				
				/* TODO restore - this is "working" just something is off
				if (! ClearsignUtil.verifyFile(new ByteArrayInputStream(Utf8Encoder.encode(result)), keyring, sb, chainsig)) {
					taskctx.returnEmpty();
					return;
				}
				*/
				
				// remove when restore above
				if (! ClearsignUtil.readFile(new ByteArrayInputStream(Utf8Encoder.encode(result)), keyring, sb)) {
					taskctx.returnEmpty();
					return;
				}
				
				CompositeStruct cres = CompositeParser.parseJson(sb);
				
				if ((cres == null) || ! (cres instanceof RecordStruct)) {
					taskctx.returnEmpty();
					return;
				}
				
				CheckManifestWork.this.manifest = (RecordStruct) cres;
							
							/*
								 {
									"Type": "Deposit",
									"Tenant": "root",
									"Site": "root",
									"Vault": "test-data",
									"SplitCount": 1,
									"Write":  [
										"\/LERKWMQLCZGE.pdf"
									 ] ,
									
									"DepositSignKey": "a43f4d081b31379f",
									"ChainSig": "xxx",
									"DepositSig": "yyy",
									"TimeStamp": "20170814T201831594Z",
									"DepositEncryptKey": "5e606836fa3006f6"
								 }
							 */
				
				// TODO check ChainSig, DepositSig, DepositEncryptKey, DepositSignKey, TimeStamp?
				
				//OperationContext.getOrThrow().getController().addVariable("Manifest", chainrec);
				
				Logger.info("Loading deposit, got and verified chain: " + CheckManifestWork.this.depositid);
				
				taskctx.returnEmpty();
			}
		});
	}
}
