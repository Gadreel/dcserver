package dcraft.filevault.work;

import dcraft.filestore.CommonPath;
import dcraft.filestore.FileStore;
import dcraft.filestore.FileStoreFile;
import dcraft.filestore.local.LocalStore;
import dcraft.filestore.local.LocalStoreFile;
import dcraft.filevault.DepositHub;
import dcraft.filevault.ManifestTransaction;
import dcraft.filevault.Vault;
import dcraft.filevault.VaultUtil;
import dcraft.hub.ResourceHub;
import dcraft.hub.app.ApplicationHub;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
import dcraft.hub.op.OperationOutcome;
import dcraft.hub.op.OperationOutcomeEmpty;
import dcraft.hub.op.OperationOutcomeStruct;
import dcraft.hub.op.UserContext;
import dcraft.hub.resource.KeyRingResource;
import dcraft.log.Logger;
import dcraft.service.ServiceHub;
import dcraft.service.ServiceRequest;
import dcraft.struct.RecordStruct;
import dcraft.struct.Struct;
import dcraft.struct.scalar.StringStruct;
import dcraft.task.IWork;
import dcraft.task.StateWork;
import dcraft.task.StateWorkStep;
import dcraft.task.Task;
import dcraft.task.TaskContext;
import dcraft.task.TaskHub;
import dcraft.task.TaskObserver;
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

public class CmsSyncWork extends StateWork {
	static public CmsSyncWork of (String nodeid) {
		CmsSyncWork work = new CmsSyncWork();
		work.prodnodeid = nodeid;
		return work;
	}
	
	protected long depositid = 0;  // "000000000000000";  15
	protected String did = null;
	protected RecordStruct manifest = null;
	protected FileStore remotedepositstore = null;		// remote
	protected LocalStore localdepositstore = null;
	protected String prodnodeid = null;
	
	protected StateWorkStep downloadchain = null;
	protected StateWorkStep verifychain = null;
	protected StateWorkStep review = null;
	protected StateWorkStep expand = null;
	protected StateWorkStep commit = null;
	protected StateWorkStep next = null;
	protected StateWorkStep done = null;
	
	protected ManifestTransaction manifestTransaction = null;

	@Override
	public void prepSteps(TaskContext trun) throws OperatingContextException {
		this.localdepositstore = LocalStore.of(ApplicationHub.getDeploymentPath().resolve("nodes/" + prodnodeid + "/deposits"));
		
		this.remotedepositstore = DepositHub.getCloudStore(null, "Production");
		
		if (this.remotedepositstore == null) {
			Logger.error("Unable to access cloud file store");
			return;
		}
		
		Path syncid = Paths.get("./deploy-" + ApplicationHub.getDeployment() + "/nodes/" + prodnodeid + "/cms-sync");
		
		if (Files.exists(syncid)) {
			CharSequence sid = IOUtil.readEntireFile(syncid);
			
			if (StringUtil.isNotEmpty(sid)) {
				Long did = StringUtil.parseInt(sid.toString().trim());
				
				if (did == null) {
					Logger.error("Bad value in cms sync");
					return;
				}
				
				this.depositid = did;
			}
		}
		
		this.formatId();
		
		this
				.withStep(downloadchain = StateWorkStep.of("Get chain file", this::download))
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
	
	public StateWorkStep download(TaskContext trun) throws OperatingContextException {
		Logger.info("Downloading: " + did);
		
		LocalStoreFile lfile = localdepositstore.resolvePathToStore("/chain/" + did + ".chain");
		
		// if local skip download
		if (lfile.exists())
			return this.verifychain;
		
		remotedepositstore.getFileDetail(CommonPath.from("/deposits/" + prodnodeid + "/chain/" + did + ".chain"), new OperationOutcome<FileStoreFile>() {
			@Override
			public void callback(FileStoreFile cfile) throws OperatingContextException {
				if ((cfile == null) || ! cfile.exists()) {
					CmsSyncWork.this.transition(trun, CmsSyncWork.this.done);
					return;
				}
				
				cfile.readAllText(new OperationOutcome<String>() {
					@Override
					public void callback(String result) throws OperatingContextException {
						lfile.writeAllText(result, new OperationOutcomeEmpty() {
							@Override
							public void callback() throws OperatingContextException {
								CmsSyncWork.this.transition(trun, CmsSyncWork.this.verifychain);
							}
						});
					}
				});
			}
		});
		
		return StateWorkStep.WAIT;
	}
	
	public StateWorkStep verify(TaskContext trun) throws OperatingContextException {
		Logger.info("Verifying: " + did);
		
		LocalStoreFile lfile = localdepositstore.resolvePathToStore("/chain/" + did + ".chain");
		
		lfile.readAllText(new OperationOutcome<String>() {
			@Override
			public void callback(String result) throws OperatingContextException {
				KeyRingResource keyring = ResourceHub.getResources().getKeyRing();
				
				StringStruct chainsig = StringStruct.ofEmpty();
				
				StringBuilder sb = new StringBuilder();
				
				ClearsignUtil.verifyFile(new ByteArrayInputStream(Utf8Encoder.encode(result)), keyring, sb, chainsig, null);
				
				manifest = Struct.objectToRecord(sb);

				CmsSyncWork.this.transition(trun, CmsSyncWork.this.review);
			}
		});
		
		return StateWorkStep.WAIT;
	}
	
	public StateWorkStep reviewDownload(TaskContext trun) throws OperatingContextException {
		Logger.info("Processing: " + did);
		
		String dtype = manifest.getFieldAsString("Type");
		
		if (! "Deposit".equals(dtype)) {
			Logger.info("Skipping, not a deposit");
			return this.next;
		}
		
		String vault = manifest.getFieldAsString("Vault");
		
		if ("NodeLogs".equals(vault) || "NodeDatabase".equals(vault)) {
			Logger.info("Skipping, backups");
			return this.next;
		}

		Vault v = VaultUtil.lookupVaultFromManifest(this.manifest);

		if (v == null)
			return this.next;

		if (v.isCmsSync()) {
			Logger.info("Process vault: " + vault);
			
			this.chainThen(
					trun,
					DownloadDepositWork.of(this.did, this.prodnodeid, this.manifest),
					this.expand
			);
			
			return StateWorkStep.WAIT;
		}
		else {
			Logger.info("Skipping, custom vault: " + vault);
		}
		
		return this.next;
	}
	
	public StateWorkStep expand(TaskContext trun) throws OperatingContextException {
		Vault v = VaultUtil.lookupVaultFromManifest(this.manifest);

		if (v == null)
			return this.done;

		this.manifestTransaction = ManifestTransaction.of(v, this.manifest, this.did, this.prodnodeid);

		this.chainThen(
				trun,
				ExpandDepositWork.of(this.did, this.prodnodeid, this.manifest, manifestTransaction.getFolder().rootFolder()),
				this.commit
		);
		
		return StateWorkStep.WAIT;
	}
	
	public StateWorkStep commit(TaskContext trun) throws OperatingContextException {
		this.chainThen(
				trun,
				new IWork() {
					@Override
					public void run(TaskContext taskctx1) throws OperatingContextException {
						TaskHub.submit(Task.ofContext(OperationContext.context(VaultUtil.userContextFromManifest(CmsSyncWork.this.manifest)))
										.withTitle("Process deposit")
										.withWork(
												new IWork() {
													@Override
													public void run(TaskContext taskctx2) throws OperatingContextException {
														manifestTransaction.commit();
														
														taskctx2.returnEmpty();
													}
												}
										),
								new TaskObserver() {
									@Override
									public void callback(TaskContext task) {
										taskctx1.returnEmpty();
									}
								}
						);
					}
				},
				this.next
		);
		
		return StateWorkStep.WAIT;
	}
	
	public StateWorkStep next(TaskContext trun) throws OperatingContextException {
		this.depositid++;
		this.formatId();

		// indicate which deposit to work in next run, if we should crash
		Path syncid = Paths.get("./deploy-" + ApplicationHub.getDeployment() + "/nodes/" + prodnodeid + "/cms-sync");
		
		IOUtil.saveEntireFile(syncid, this.depositid + "");
		
		return this.downloadchain;
	}
	
	public StateWorkStep done(TaskContext trun) throws OperatingContextException {
		Logger.info("All available deposits processed");
		
		// indicate which deposit to work in next run
		Path syncid = Paths.get("./deploy-" + ApplicationHub.getDeployment() + "/nodes/" + prodnodeid + "/cms-sync");
		
		IOUtil.saveEntireFile(syncid, this.depositid + "");
		
		return StateWorkStep.NEXT;
	}
}
