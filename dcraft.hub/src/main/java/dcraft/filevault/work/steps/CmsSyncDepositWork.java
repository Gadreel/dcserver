package dcraft.filevault.work.steps;

import dcraft.filestore.CollectionSourceStream;
import dcraft.filestore.CommonPath;
import dcraft.filestore.FileCollection;
import dcraft.filestore.FileDescriptor;
import dcraft.filestore.FileStore;
import dcraft.filestore.local.LocalStore;
import dcraft.filestore.local.LocalStoreFile;
import dcraft.filevault.DepositHub;
import dcraft.filevault.Transaction;
import dcraft.filevault.Vault;
import dcraft.hub.app.ApplicationHub;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
import dcraft.log.Logger;
import dcraft.stream.ReturnOption;
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
import dcraft.task.ChainWork;
import dcraft.task.ControlWork;
import dcraft.task.IWork;
import dcraft.task.TaskContext;
import dcraft.tenant.TenantHub;
import dcraft.util.StringUtil;

import java.io.IOException;
import java.nio.file.Files;

public class CmsSyncDepositWork extends ChainWork {
	static public CmsSyncDepositWork of (String nodeid, String depositid, FileStore depositstore) {
		CmsSyncDepositWork work = new CmsSyncDepositWork();
		work.nodeid = nodeid;
		work.depositid = depositid;
		work.depositstore = depositstore;
		return work;
	}
	
	protected String nodeid = null;
	protected String depositid = null;
	protected FileStore depositstore = null;
	
	@Override
	protected void init(TaskContext taskctx) throws OperatingContextException {
		if (StringUtil.isEmpty(this.nodeid)) {
			Logger.error("Missing node id");
			taskctx.returnEmpty();
			return;
		}
		
		if (StringUtil.isEmpty(this.depositid)) {
			Logger.error("Missing deposit id");
			taskctx.returnEmpty();
			return;
		}
		
		Logger.info("Loading deposit: " + this.depositid + " from " + this.nodeid);
		
		DownloadManifestWork downloadchain = DownloadManifestWork.of(this.nodeid, this.depositid, this.depositstore);
		
		this
				.then(downloadchain)
				.then(ControlWork.dieOnError("Unable to download chain file"))
				.then(new ChainWork() {
					@Override
					protected void init(TaskContext taskctx) throws OperatingContextException {
						LocalStoreFile local = downloadchain.getLocal();
						
						CheckManifestWork checkwork = CheckManifestWork.of(CmsSyncDepositWork.this.depositid, local);
		
						this
								.then(checkwork)
								.then(ControlWork.dieOnError("Unable to check deposit chain file"))
								.then(new ChainWork() {
									@Override
									protected void init(TaskContext taskctx) throws OperatingContextException {
										RecordStruct manifest = checkwork.getManifest();
										
										boolean downloadflag = true;
										
										String type = manifest.getFieldAsString("Type");
										
										if (! "Deposit".equals(type)) {
											downloadflag = false;
										}
										
										String vault = manifest.getFieldAsString("Vault");
										
										if (! "Galleries".equals(vault) && ! "Files".equals(vault) && ! "Feeds".equals(vault) && ! "SiteFiles".equals(vault)) {
											downloadflag = false;
										}
										
										if (! downloadflag) {
											Logger.info("Skipping deposit: " + depositid + " - Type " + type + " - Vault " + vault);
											taskctx.returnEmpty();
											return;
										}
										
										String tenant = manifest.getFieldAsString("Tenant");
										String site = manifest.getFieldAsString("Site");
										
										Logger.info("Update for " + tenant + " : " + site + " : " + vault);
										
										System.out.println(manifest.toPrettyString());
										
										/* TODO
										Vault vaultman = TenantHub.resolveTenant(tenant).resolveSite(site).getVault(vault);
										
										DownloadDepositWork downnloaddeposit = DownloadDepositWork.of(nodeid, depositid, depositstore, manifest);
										
										this
												.then(downnloaddeposit)
												.then(ControlWork.dieOnError("Unable to download deposit files"))
												.then(new ChainWork() {
													@Override
													protected void init(TaskContext taskctx) throws OperatingContextException {
														RecordStruct manifest = checkwork.getManifest();
														
														ExpandDepositWork expanddeposit = ExpandDepositWork.of(nodeid, depositid, (LocalStoreFile) vaultman.getFileStore().rootFolder(), manifest);

														this
																.then(expanddeposit)
																.then(ControlWork.dieOnError("Unable to expand deposit files"));
														
														// TODO add support for dcFileIndex
														// TODO add support for delete files
													}
												});
												*/
									}
								});
					}
				});
	}
}
