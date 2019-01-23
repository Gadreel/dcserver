package dcraft.filevault.work;

import dcraft.db.BasicRequestContext;
import dcraft.db.DatabaseAdapter;
import dcraft.db.DatabaseException;
import dcraft.db.IConnectionManager;
import dcraft.db.fileindex.FileIndexAdapter;
import dcraft.db.util.ByteUtil;
import dcraft.filestore.*;
import dcraft.filestore.aws.AwsStore;
import dcraft.filestore.aws.AwsStoreFile;
import dcraft.filestore.local.LocalDestStream;
import dcraft.filestore.local.LocalStoreFile;
import dcraft.filevault.DepositHub;
import dcraft.filevault.Vault;
import dcraft.hub.ResourceHub;
import dcraft.hub.app.ApplicationHub;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationOutcome;
import dcraft.hub.op.OperationOutcomeEmpty;
import dcraft.hub.resource.KeyRingResource;
import dcraft.hub.resource.ResourceBase;
import dcraft.log.Logger;
import dcraft.scriptold.StackEntry;
import dcraft.stream.ReturnOption;
import dcraft.stream.StreamFragment;
import dcraft.stream.StreamUtil;
import dcraft.stream.StreamWork;
import dcraft.stream.file.*;
import dcraft.struct.*;
import dcraft.struct.scalar.IntegerStruct;
import dcraft.struct.scalar.StringStruct;
import dcraft.task.*;
import dcraft.tenant.TenantHub;
import dcraft.util.HashUtil;
import dcraft.util.HexUtil;
import dcraft.util.pgp.ClearsignUtil;
import dcraft.util.pgp.KeyRingCollection;
import dcraft.xml.XElement;
import org.bouncycastle.openpgp.PGPException;
import org.bouncycastle.openpgp.PGPPublicKeyRing;
import org.bouncycastle.openpgp.PGPSecretKeyRing;
import org.bouncycastle.openpgp.PGPSignature;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class BuildDepositWork extends ChainWork {
	protected Path currtrigger = null;
	protected boolean newrun = true;
	
	public void setCurrtrigger(Path v) {
		this.currtrigger = v;
	}
	
	public BuildDepositWork withStartTrigger(Path v) {
		this.currtrigger = v;
		return this;
	}
	
	
	/*
.chain contents

{
	SplitCount: NNN  (number of tgzp-NNNNN files)
	TimeStamp: NNNN-NN-NNTNN:NN.NNNZ
	Type: NodeAudit|Deposit|Work|WorkAudit    (DEPOSIT triggers the sync work , WORK has work id of file tx in feed)
	TargetHub: NNNNN      (hub id if work, optional)
	TargetTopic: NNNNN      (topic if work, optional)
	Tenant: NNNN   (if deposit)
	Site: NNNN      (if deposit)
	Vault: NNNN   (if deposit)
	ChainSig: NNNNNN     (hash of previous chain file)
	DepositSig: NNNNNN,   (has of current deposit as held by current sig - must agree on alg and val)
	DepositSignKey: hex of key id,    (just makes it harder to tamper with)
	DepositEncryptKey: hex of key id,   (just makes it harder to tamper with)
	Delete: [
		'path', 'path', 'path'
	]
	Write: [                 (just an index, so we know what is in the deposit without reading it)
		'path', 'path', 'path'
	]
}

ASCII armored chain sig of all content of the .chain file   (node signing key)
	
	 */
	
	// TODO system messages if anything fails here
	
	@Override
	public void run(TaskContext taskctx) throws OperatingContextException {
		if (this.newrun) {
			this.newrun = false;
			
			// done
			if (this.currtrigger == null) {
				taskctx.returnEmpty();
				return;
			}
			
			Logger.info("Building deposit: " + this.currtrigger);
			
			// verify that the previous deposit sequence # has uploaded
			// create a deposit file from TAR - deposit sequence number - and sig and chain
			// copy deposit files to the cloud storage, if any
			// TODO Ignite will index deposit in cloud database
			
			if (! Files.exists(this.currtrigger)) {
				Logger.error("Next deposit trigger path does not exist, unable to build: " + this.currtrigger);
				taskctx.returnEmpty();
				return;
			}
			
			CompositeStruct res = CompositeParser.parseJson(this.currtrigger);
			
			if ((res == null) || ! (res instanceof RecordStruct)) {
				Logger.error("Error loading trigger data");
				taskctx.returnEmpty();
				return;
			}
			
			RecordStruct params = (RecordStruct) res;
			
			// get cloud file store
			FileStore store = DepositHub.getCloudStore(null, null);
			
			if (store == null)
				Logger.warn("Missing cloud file store for deposits");
			
			// this is a one time use number
			String depositId = params.getFieldAsString("DepositId");
			
			if (depositId == null) {
				Logger.error("Missing deposit id");
				taskctx.returnEmpty();
				return;
			}
			
			KeyRingResource keyring = ResourceHub.getResources().getKeyRing();
			
			PGPPublicKeyRing encryptor = keyring.findUserPublicKey("encryptor@" + ApplicationHub.getDeployment() + ".dc");
			
			PGPSecretKeyRing seclocalsign = keyring.findUserSecretKey(ApplicationHub.getNodeId()
					+ "-signer@" + ApplicationHub.getDeployment() + ".dc");
			
			// only works because we are in root tenant and so is this vault
			String transactionid = params.getFieldAsString("Transaction");
			
			FileCollection finalfiles = new FileCollection();
			IntegerStruct finalcount = IntegerStruct.of(0L);
			StringStruct finalsig = StringStruct.ofEmpty();
			StringStruct chainsig = StringStruct.ofEmpty();
			
			// TODO skip if deposit already written
			Path outpath = DepositHub.DepositStore.resolvePath("/outstanding");
			
			if (Files.notExists(outpath)) {
				Logger.error("Missing outstanding deposits");
				taskctx.returnEmpty();
				return;
			}
			
			//RecordStruct manifestrec = params.getFieldAsRecord("Manifest");
			
			// TODO check and skip the deposit build if that step is already done
			
			Path tarpath = outpath.resolve(transactionid + ".tgzg");
			
			boolean tarfiles = Files.exists(tarpath);
			
			// create a deposit file from PGP - deposit sequence number - and sig and chain
			LocalStoreFile sigfile = DepositHub.DepositStore.resolvePathToStore("/files/" + depositId + ".sig");
			
			if (tarfiles) {
				finalfiles.withFiles(sigfile);
			}

			StreamFragment fragment = StreamUtil.localFile(tarpath).allocStreamSrc();

			fragment.withAppend(
					new PgpSignStream().withOutputFile(sigfile.getLocalPath())
							.withSignKey(seclocalsign)		// TODO get hash out
							.withPassphrase(keyring.getPassphrase())
							.withSigVar(finalsig),
					new SplitStream()
							.withNameTemplate(depositId + ".tgzp-%seq%")
							.withDashNumMode(false)
							.withSize(4294967296L)    // 4 GB
							.withCountVar(finalcount),
					FileObserverStream.of(new Consumer<FileDescriptor>() {
						@Override
						public void accept(FileDescriptor file) {
							if (! file.isFolder()) {
								LocalStoreFile dfile = DepositHub.DepositStore.resolvePathToStore("/files/" + file.getName());
								finalfiles.withFiles(dfile);
							}
						}
					})
			)
			.withAppend(
					DepositHub.DepositStore.fileReference(CommonPath.from("/files"), true).allocStreamDest()
			);

			IWork builddeposit = StreamWork.of(fragment);
			
			// update the manifest, create chain file
			
			IWork buildstoremanifest = new IWork() {
				@Override
				public void run(TaskContext taskctx) throws OperatingContextException {
					RecordStruct manifestrec = params.getFieldAsRecord("Manifest");
					
					/* we can now get this
					.with("Manifest", RecordStruct.record()
							.with("TimeStamp", TimeUtil.now())
							.with("Type", "Deposit")
							.with("Tenant", OperationContext.getOrThrow().getTenant().getAlias())
							.with("Site", OperationContext.getOrThrow().getSite().getAlias())
							.with("Vault", vaultname)
							.with("Write", ListStruct.list())
							.with("Delete", ListStruct.list())
					)
					
					and then add these things
					
					{
						SplitCount: NNN  (number of tgzp-NNNNN files)
						ChainSig: NNNNNN     (hash of previous chain file)
						DepositSig: NNNNNN,   (has of current deposit as held by current sig - must agree on alg and val)
						DepositSignKey: hex of key id,    (just makes it harder to tamper with)
						DepositEncryptKey: hex of key id,   (just makes it harder to tamper with)
					}
					
					
					*/
					
					if (finalcount.getValue() == 0) {
						manifestrec
								.with("SplitCount", finalcount)
								.with("ChainSig", DepositHub.getLastSig(ApplicationHub.getNodeId()));
					}
					else {
						manifestrec
								.with("SplitCount", finalcount)
								.with("ChainSig", DepositHub.getLastSig(ApplicationHub.getNodeId()))
								.with("DepositSig", finalsig)
								.with("DepositSignKey", Long.toHexString(seclocalsign.getPublicKey().getKeyID()))
								.with("DepositEncryptKey", Long.toHexString(encryptor.getPublicKey().getKeyID()));
					}
					
					String chain = manifestrec.toPrettyString();
					
					ClearsignUtil.ClearSignResult csresult = ClearsignUtil.clearSignMessage(chain, keyring, seclocalsign, keyring.getPassphrase());
					
					try {
						chainsig.setValue(HexUtil.bufferToHex(csresult.sig.getSignature()));
					}
					catch (PGPException x) {
						Logger.error("Unable to collect chain sig");
					}
					
					LocalStoreFile chainfile = DepositHub.DepositStore.resolvePathToStore("/chain/" + depositId + ".chain");
					
					chainfile.writeAllText(csresult.file, new OperationOutcomeEmpty() {
						@Override
						public void callback() throws OperatingContextException {
							if (store != null) {
								FileStoreFile sf = store.fileReference(CommonPath.from("/deposits/" + ApplicationHub.getNodeId() + "/chain/" + depositId + ".chain"));
								
								sf.writeAllText(csresult.file, new OperationOutcomeEmpty() {
									@Override
									public void callback() throws OperatingContextException {
										// index the deposit if local database
										if (ResourceHub.getResources().getDatabases().hasDefaultDatabase()) {
											IConnectionManager connectionManager = ResourceHub.getResources().getDatabases().getDatabase();
											
											DatabaseAdapter adapter = connectionManager.allocateAdapter();
											
											try {
												adapter.set("root", "dcDepositIndex", ApplicationHub.getNodeId(), depositId, chain);
											}
											catch (DatabaseException x) {
												Logger.error("Unable to index deposit in db: " + x);
											}
										}
										
										taskctx.returnEmpty();
									}
								});
							}
							else {
								// index the deposit if local database
								if (ResourceHub.getResources().getDatabases().hasDefaultDatabase()) {
									IConnectionManager connectionManager = ResourceHub.getResources().getDatabases().getDatabase();
									
									DatabaseAdapter adapter = connectionManager.allocateAdapter();
									
									try {
										adapter.set("root", "dcDepositIndex", ApplicationHub.getNodeId(), depositId, chain);
									}
									catch (DatabaseException x) {
										Logger.error("Unable to index deposit in db: " + x);
									}
								}
								
								taskctx.returnEmpty();
							}
						}
					});
				}
			};
			
			// set us up to do another round of uploads
			IWork findnext = new IWork() {
				@Override
				public void run(TaskContext taskctx) throws OperatingContextException {
					try {
						Files.deleteIfExists(BuildDepositWork.this.currtrigger);
					}
					catch (IOException x) {
						Logger.error("Error removing deposit trigger: " + x);
					}
					
					try {
						Files.deleteIfExists(outpath.resolve(transactionid + ".tgzg"));
					}
					catch (IOException x) {
						Logger.error("Error removing deposit pgp: " + x);
					}
					
					// allow next deposit to upload
					DepositHub.clearDepositId(ApplicationHub.getNodeId(), depositId, chainsig.getValueAsString());
					
					BuildDepositWork.this.currtrigger = DepositHub.nextDepositOnQueue();
					BuildDepositWork.this.newrun = true;
					
					taskctx.returnEmpty();
				}
			};
			
			if (tarfiles) {
				this
						.then(builddeposit);
			}

			this
					.then(ControlWork.dieOnError("Unable to create deposit files"))
					.then(new IWork() {
						@Override
						public void run(TaskContext taskctx) throws OperatingContextException {
							finalfiles.forEach(new OperationOutcome<FileStoreFile>() {
								@Override
								public void callback(FileStoreFile result) throws OperatingContextException {
									if (result instanceof LocalStoreFile)
										((LocalStoreFile) result).refreshProps();
								}
							});
							
							finalfiles.resetPosition();
							
							// TODO this isn't right - not if the above was true async
							taskctx.returnEmpty();
						}
					});
			
			
			// upload the deposit files
			
			// TODO check and skip upload deposit if step is already done
			
			if (store != null) {
					this.then(StreamWork.of(CollectionSourceStream.of(finalfiles))
							.with(
									store.fileReference(CommonPath.from("/deposits/" + ApplicationHub.getNodeId()), true)
											.allocStreamDest()
							));
			}
			
			this
					.then(ControlWork.dieOnError("Unable to upload deposit files"))
					.then(buildstoremanifest)
					.then(ControlWork.dieOnError("Unable to chain deposit files"))
					.then(findnext);
			
			// TODO if global update the cloud DB and create an audit deposit - consider for Ignite
		}
		
		super.run(taskctx);
	}
}
