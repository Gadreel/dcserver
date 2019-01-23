package dcraft.filevault.work;

import dcraft.filestore.*;
import dcraft.filestore.local.LocalStore;
import dcraft.filestore.local.LocalStoreFile;
import dcraft.filevault.DepositHub;
import dcraft.filevault.Transaction;
import dcraft.filevault.Vault;
import dcraft.hub.ResourceHub;
import dcraft.hub.app.ApplicationHub;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
import dcraft.hub.op.OperationOutcome;
import dcraft.hub.resource.KeyRingResource;
import dcraft.log.Logger;
import dcraft.stream.ReturnOption;
import dcraft.stream.StreamFragment;
import dcraft.stream.StreamWork;
import dcraft.stream.file.*;
import dcraft.struct.CompositeParser;
import dcraft.struct.CompositeStruct;
import dcraft.struct.RecordStruct;
import dcraft.struct.scalar.StringStruct;
import dcraft.task.ChainWork;
import dcraft.task.ControlWork;
import dcraft.task.IWork;
import dcraft.task.TaskContext;
import dcraft.tenant.Site;
import dcraft.tenant.Tenant;
import dcraft.tenant.TenantHub;
import dcraft.util.StringUtil;
import dcraft.util.chars.Utf8Encoder;
import dcraft.util.pgp.ClearsignUtil;
import org.bouncycastle.openpgp.PGPPublicKeyRing;
import org.bouncycastle.openpgp.PGPSecretKeyRing;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class LoadDepositWork extends ChainWork {
	protected Path currtrigger = null;
	protected String nodeid = null;
	protected boolean newrun = true;
	
	public void setCurrtrigger(Path v) {
		this.currtrigger = v;
	}
	
	public LoadDepositWork withStartTrigger(Path v) {
		this.currtrigger = v;
		return this;
	}
	
	public LoadDepositWork withNodeId(String v) {
		this.nodeid = v;
		return this;
	}
	
	/*  #######################################
	
	!!!	TODO review CmsSyncWork + DownloadDeposit and adopt steps
	
	####################################### */
	
	
	@Override
	public void run(TaskContext taskctx) throws OperatingContextException {
		if (this.newrun) {
			this.newrun = false;
			
			// done
			if (this.currtrigger == null) {
				taskctx.returnEmpty();
				return;
			}
			
			Logger.info("Loading deposit: " + this.currtrigger);
			
			// TODO Ignite will index deposit in cloud database
			// TODO (and Index in Vault if Indexed mode)
			
			if (! Files.exists(this.currtrigger)) {
				Logger.error("Next remote deposit trigger path does not exist, unable to download: " + this.currtrigger);
				taskctx.returnEmpty();
				return;
			}
			
			CompositeStruct res = CompositeParser.parseJson(this.currtrigger);
			
			if ((res == null) || ! (res instanceof RecordStruct)) {
				Logger.error("Unable to track remote deposits, error loading tracker data");
				taskctx.returnEmpty();
				return;
			}
			
			RecordStruct params = (RecordStruct) res;
			
			// get cloud file store
			FileStore store = DepositHub.getCloudStore(null, null);
			
			if (store == null) {
				Logger.error("Unable to access cloud file store");
				taskctx.returnEmpty();
				return;
			}
			
			// this is a one time use number
			String depositId = params.getFieldAsString("DepositId");
			
			if (depositId == null) {
				Logger.error("Missing deposit id");
				taskctx.returnEmpty();
				return;
			}
			
			LocalStore nodeDepositStore = LocalStore.of(ApplicationHub.getDeploymentPath().resolve("nodes/" + nodeid + "/deposits"));
			
			LocalStoreFile chainfile = nodeDepositStore.resolvePathToStore("/chain/" + depositId + ".chain");

			StreamFragment fragment = store.fileReference(CommonPath.from("/deposits/" + this.nodeid
					+ "/chain/" + depositId + ".chain"))
					.allocStreamSrc();

			fragment.withAppend(chainfile.allocStreamDest());

			IWork downloadchain = StreamWork.of(fragment);
			
			if (chainfile.exists())
				downloadchain = new IWork() {
					@Override
					public void run(TaskContext taskctx) throws OperatingContextException {
						Logger.info("Chain file already present.");
						taskctx.returnEmpty();
					}
				};
			
			KeyRingResource keyring = ResourceHub.getResources().getKeyRing();
			
			/*
			PGPPublicKeyRing encryptor = keyring.findUserPublicKey("encryptor@" + ApplicationHub.getDeployment() + ".dc");
			
			PGPPublicKeyRing publocalsign = keyring.findUserPublicKey(ApplicationHub.getNodeId()
					+ "-signer@" + ApplicationHub.getDeployment() + ".dc");
			
			PGPSecretKeyRing seclocalsign = keyring.findUserSecretKey(ApplicationHub.getNodeId()
					+ "-signer@" + ApplicationHub.getDeployment() + ".dc");
			
			char[] passpharse = keyring.getPassphrase();
			*/
			
			StringStruct chainsig = StringStruct.ofEmpty();
			
			IWork checkmanifest = new IWork() {
				@Override
				public void run(TaskContext taskctx) throws OperatingContextException {
					chainfile.refreshProps();

					chainfile.readAllText(new OperationOutcome<String>() {
						@Override
						public void callback(String result) throws OperatingContextException {
							StringBuilder sb = new StringBuilder();
							
							if (! ClearsignUtil.verifyFile(new ByteArrayInputStream(Utf8Encoder.encode(result)), keyring, sb, chainsig)) {
								taskctx.returnEmpty();
								return;
							}

							CompositeStruct cres = CompositeParser.parseJson(sb);
					
							if ((cres == null) || ! (cres instanceof RecordStruct)) {
								taskctx.returnEmpty();
								return;
							}
							
							RecordStruct chainrec = (RecordStruct) cres;
							
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
							
							OperationContext.getOrThrow().getController().addVariable("Manifest", chainrec);
							
							Logger.info("Loading deposit, got and verified chain: " + LoadDepositWork.this.currtrigger);
							
							taskctx.returnEmpty();
						}
					});
				}
			};
			
			// TODO handle deletes from deposit manifest
			
			IWork downloaddeposit = new ChainWork() {
				protected boolean first = true;
				
				@Override
				public void run(TaskContext taskctx) throws OperatingContextException {
					if (this.first) {
						this.first = false;
						
						RecordStruct manifest = (RecordStruct) OperationContext.getOrThrow().getController().queryVariable("Manifest");
						
						System.out.println("download got: " + manifest);
						
						if (! "Deposit".equals(manifest.getFieldAsString("Type"))) {
							Logger.error("Unknown deposit type: " + manifest.getFieldAsString("Type"));
							taskctx.returnEmpty();
							return;
						}
						
						int cnt = (int) manifest.getFieldAsInteger("SplitCount", 0);
						
						// TODO can be zero if deletes are present
						if (cnt <= 0) {
							Logger.error("Invalid SplitCount: " + cnt);
							taskctx.returnEmpty();
							return;
						}
						
						// TODO make sure we verify the .sig and - when we use it - the deposit
						
						LocalStore nodeDepositStore = LocalStore.of(ApplicationHub.getDeploymentPath().resolve("nodes/" + LoadDepositWork.this.nodeid + "/deposits"));
						
						FileCollection finalfiles = new FileCollection();
						
						LocalStoreFile chkfile = nodeDepositStore.resolvePathToStore("/files/" + depositId + ".sig");
						
						if (! chkfile.exists())
							finalfiles.withFiles(store.fileReference(CommonPath.from("/deposits/" + LoadDepositWork.this.nodeid
									+ "/files/" + depositId + ".sig")));

						for (int i = 1; i <= cnt; i++) {
							String fname = "/files/" + depositId + ".tgzp-" + StringUtil.leftPad(i + "", 4, '0');
							
							LocalStoreFile chkfiled = nodeDepositStore.resolvePathToStore(fname);
							
							if (! chkfiled.exists())
								finalfiles.withFiles(store.fileReference(CommonPath.from("/deposits/" + LoadDepositWork.this.nodeid
										+ fname)));
						}
						
						if (finalfiles.getSize() == 0) {
							Logger.info("Deposit files already present.");
							taskctx.returnEmpty();
							return;
						}
						
						//LocalStoreFile localfiles = nodeDepositStore.resolvePathToStore("/files");
						
						this.then(StreamWork.of(
								CollectionSourceStream.of(finalfiles),
								new TransformFileStream() {
									@Override
									public ReturnOption handle(FileSlice slice) throws OperatingContextException {
										if (slice != FileSlice.FINAL) {
											// rename path for local
											FileDescriptor fd = slice.getFile();
											fd.with("Path", "/files/" + fd.getName());
										}
										
										return this.consumer.handle(slice);
									}
								})
								.with(nodeDepositStore.rootFolder().allocStreamDest())
						);
					}
					
					super.run(taskctx);
				}
			};
			
			
			String txid = Transaction.createTransactionId();
			
			IWork processdeposit = new ChainWork() {
				protected boolean first = true;
				
				@Override
				public void run(TaskContext taskctx) throws OperatingContextException {
					if (this.first) {
						this.first =false;
						
						RecordStruct manifest = (RecordStruct) OperationContext.getOrThrow().getController().queryVariable("Manifest");
						
						System.out.println("download got: " + manifest);
						
						if (! "Deposit".equals(manifest.getFieldAsString("Type"))) {
							Logger.error("Unknown deposit type: " + manifest.getFieldAsString("Type"));
							taskctx.returnEmpty();
							return;
						}
						
						int cnt = (int) manifest.getFieldAsInteger("SplitCount", 0);
						
						if (cnt <= 0) {
							Logger.error("Invalid SplitCount: " + cnt);
							taskctx.returnEmpty();
							return;
						}
						
						// TODO make sure we verify the .sig and - when we use it - the deposit
						
						LocalStore nodeDepositStore = LocalStore.of(ApplicationHub.getDeploymentPath().resolve("nodes/" + LoadDepositWork.this.nodeid + "/deposits"));
						
						FileCollection finalfiles = new FileCollection();
						
						LocalStoreFile chkfile = nodeDepositStore.resolvePathToStore("/files/" + depositId + ".sig");
						
						for (int i = 1; i <= cnt; i++) {
							String fname = "/files/" + depositId + ".tgzp-" + StringUtil.leftPad(i + "", 4, '0');
							finalfiles.withFiles(nodeDepositStore.resolvePathToStore(fname));
						}
						
						
						// TODO add support for dcFileIndex
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
									new UntarStream()
							)
							.with(LocalStore.of(DepositHub.DepositStore.resolvePath("/transactions/" + txid)).rootFolder().allocStreamDest())
						);
					}
					
					super.run(taskctx);
				}
			};
			
			// set us up to do another round of uploads
			IWork findnext = new IWork() {
				@Override
				public void run(TaskContext taskctx) throws OperatingContextException {
					RecordStruct manifest = (RecordStruct) OperationContext.getOrThrow().getController().queryVariable("Manifest");
					
					if (manifest.isFieldEmpty("Tenant") || manifest.isFieldEmpty("Site") || manifest.isFieldEmpty("Vault")) {
						Logger.error("Missing tenant, site or vault name");
						taskctx.returnEmpty();
						return;
					}
					
					/* TODO provide an "delayed" mode where some vaults do this step here - during the work - while other vaults do it earlier
					String tenant = manifest.getFieldAsString("Tenant");
					String site = manifest.getFieldAsString("Site");
					String vault = manifest.getFieldAsString("Vault");
					
					Tenant tenant1 = TenantHub.resolveTenant(tenant);
					
					if (tenant1 == null) {
						Logger.error("Missing tenant: " + tenant);
						taskctx.returnEmpty();
						return;
					}
					
					Site site1 = tenant1.resolveSite(site);
					
					if (site1 == null) {
						Logger.error("Missing site: " + tenant + " - " + site);
						taskctx.returnEmpty();
						return;
					}
					
					Vault vault1 = site1.getVault(vault);
					
					if (vault1 == null) {
						Logger.error("Missing vault: " + tenant + " - " + site + " - " + vault);
						taskctx.returnEmpty();
						return;
					}
					
					Transaction tx = Transaction.of(txid, vault1);
					
					// TODO don't overwrite newer files (mod) and don't add files that have since been deleted - see index
					tx.commitInternalTransaction();
					*/
					
					try {
						Files.deleteIfExists(LoadDepositWork.this.currtrigger);
					}
					catch (IOException x) {
						Logger.error("Error removing remote deposit trigger: " + x);
					}
					
					// allow next deposit to upload (download?)
					DepositHub.clearDepositId(LoadDepositWork.this.nodeid, depositId, chainsig.getValueAsString());
					
					LoadDepositWork.this.currtrigger = DepositHub.nextRemoteDepositOnQueue(LoadDepositWork.this.nodeid);
					LoadDepositWork.this.newrun = true;
					
					taskctx.returnEmpty();
				}
			};
			
			this
					.then(downloadchain)
					.then(ControlWork.dieOnError("Unable to download chain file"))
					.then(checkmanifest)
					.then(ControlWork.dieOnError("Unable to check deposit chain file"))
					.then(downloaddeposit)
					.then(ControlWork.dieOnError("Unable to download deposit files"))
					.then(processdeposit)
					.then(ControlWork.dieOnError("Unable to process deposit files"))
					.then(findnext);
			
			// TODO if global update the cloud DB and create an audit deposit - consider for Ignite
		}
		
		super.run(taskctx);
	}
}
