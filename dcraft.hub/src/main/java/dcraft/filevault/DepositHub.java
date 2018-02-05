package dcraft.filevault;

import dcraft.db.DatabaseAdapter;
import dcraft.db.DatabaseException;
import dcraft.db.IConnectionManager;
import dcraft.db.util.ByteUtil;
import dcraft.filestore.CommonPath;
import dcraft.filestore.FileStore;
import dcraft.filestore.FileStoreFile;
import dcraft.filestore.aws.AwsStore;
import dcraft.filestore.local.LocalStore;
import dcraft.filevault.work.BuildDepositWork;
import dcraft.filevault.work.LoadDepositWork;
import dcraft.hub.ResourceHub;
import dcraft.hub.app.ApplicationHub;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
import dcraft.hub.op.OperationOutcome;
import dcraft.hub.time.BigDateTime;
import dcraft.log.Logger;
import dcraft.struct.CompositeParser;
import dcraft.struct.CompositeStruct;
import dcraft.struct.FieldStruct;
import dcraft.struct.ListStruct;
import dcraft.struct.RecordStruct;
import dcraft.struct.Struct;
import dcraft.task.*;
import dcraft.task.run.WorkTopic;
import dcraft.util.IOUtil;
import dcraft.util.StringUtil;
import dcraft.util.TimeUtil;
import dcraft.util.chars.Utf8Encoder;
import dcraft.xml.XElement;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

// TODO on start load previous values - nextid and lasthash
public class DepositHub {
	final static public LocalStore DepositStore = LocalStore.of(ApplicationHub.getDeploymentNodePath().resolve("deposits"));
	
	// only one id may be processed at a time, current must be cleared before the next can go
	static protected AtomicLong nextid = new AtomicLong();
	static protected RandomAccessFile status = null;
	
	//static protected String lastsig = null;
	//static protected String lastdeposit = null;
	
	static protected ReentrantLock depositlock = new ReentrantLock();
	static protected RecordStruct depositstatus = RecordStruct.record();
	
	//static
	
	// TODO load save the data files
	
	static public String allocateDepositId() {
		long num = DepositHub.nextid.getAndIncrement();
		
		if (num > 999999999999999L) {
			//DepositHub.nextid.set(0);
			//num = 0;
			Logger.error("Ran out of deposit IDs for this node, switch to a new node id.");
			return null;
		}
		
		if (! DepositHub.updateTracking())
			return null;
		
		return StringUtil.leftPad(num + "", 15, '0');
	}
	
	static public void clearDepositId(String nodeid, String depositid, String lastsig) {
		DepositHub.depositlock.lock();
		
		try {
			RecordStruct lstate = DepositHub.depositstatus.getFieldAsRecord(nodeid);
			
			if (lstate == null) {
				lstate = RecordStruct.record();
				DepositHub.depositstatus.with(nodeid, lstate);
			}
			
			lstate.with("LastSig", lastsig);
			lstate.with("LastDeposit", depositid);
			
			DepositHub.updateTracking();
		}
		finally {
			DepositHub.depositlock.unlock();
		}
	}
	
	static public void clearLastChain(String remotenodeid, String nextid) {
		DepositHub.depositlock.lock();
		
		try {
			RecordStruct lstate = DepositHub.depositstatus.getFieldAsRecord(remotenodeid);
			
			if (lstate == null) {
				lstate = RecordStruct.record();
				DepositHub.depositstatus.with(remotenodeid, lstate);
			}
			
			lstate.with("LastChain", nextid);
			
			DepositHub.updateTracking();
		}
		finally {
			DepositHub.depositlock.unlock();
		}
	}
	
	// should be safe, but maybe lock
	static public String getLastSig(String nodeid) {
		RecordStruct lstate = DepositHub.depositstatus.getFieldAsRecord(nodeid);
		
		if (lstate != null)
			return lstate.getFieldAsString("LastSig");
		
		return null;
	}
	
	static public boolean submitVaultDeposit(String transactionid, String vaultname, List<CommonPath> deletes) throws OperatingContextException {
		// create a resil trigger file so we are found if server stops
		String depositId = DepositHub.allocateDepositId();
		
		if (depositId == null)
			return false;
		
		RecordStruct trec = RecordStruct.record()
				.with("Manifest", RecordStruct.record()
						.with("TimeStamp", TimeUtil.now())
						.with("Type", "Deposit")
						.with("Tenant", OperationContext.getOrThrow().getTenant().getAlias())
						.with("Site", OperationContext.getOrThrow().getSite().getAlias())
						.with("Vault", vaultname)
						.with("Delete", ListStruct.list().withCollection(deletes))
				)
				.with("Transaction", transactionid)
				.with("DepositId", depositId);

		// allocate (above) ensures folder exists
		Path triggpath = DepositHub.DepositStore.resolvePath("/triggers/"
				+ ApplicationHub.getNodeId() + "-" + depositId + ".json");
		
		if (Files.exists(triggpath)) {
			Logger.error("Deposit trigger path already exists, unable to add: " + triggpath);
			return false;
		}
		
		return IOUtil.saveEntireFile(triggpath, trec.toPrettyString());
	}
	
	static public void enableQueueChecker() {
		Task peroidicChecker = Task.ofHubRoot()
				.withTitle("Review local deposit triggers")
				.withTopic(WorkTopic.SYSTEM)
				.withNextId("QUEUE")
				.withWork(new DepositHub.PeriodicTriggerReviewWork());
		
		TaskHub.scheduleIn(peroidicChecker, 5);		// TODO switch to 110
		
		Task startChecker = Task.ofHubRoot()
				.withTitle("Start periodic review of local deposit triggers")
				.withTopic(WorkTopic.SYSTEM)
				.withNextId("QUEUE")
				.withWork(new IWork() {
					@Override
					public void run(TaskContext taskctx) throws OperatingContextException {
						// sweep the triggers folder every 5 seconds
						TaskHub.scheduleEvery(peroidicChecker, 5);
						taskctx.returnEmpty();
					}
				});
		
		TaskHub.scheduleIn(startChecker, 60);			// TODO switch to 150
	}
	
	static public void enableRemoteChecker() {
		Task peroidicChecker = Task.ofHubRoot()
				.withTitle("Review remote deposits")
				.withTopic(WorkTopic.SYSTEM)
				.withNextId("QUEUE")
				.withWork(new DepositHub.PeriodicRemoteWork());
		
		TaskHub.scheduleIn(peroidicChecker, 15);		// TODO switch to 180
		
		Task startChecker = Task.ofHubRoot()
				.withTitle("Start periodic review of remote deposits")
				.withTopic(WorkTopic.SYSTEM)
				.withNextId("QUEUE")
				.withWork(new IWork() {
					@Override
					public void run(TaskContext taskctx) throws OperatingContextException {
						// sweep the triggers folder every 5 seconds
						TaskHub.scheduleEvery(peroidicChecker, 60);
						taskctx.returnEmpty();
					}
				});
		
		TaskHub.scheduleIn(startChecker, 60);			// TODO switch to 600
	}
	
	static public boolean updateTracking() {
		DepositHub.depositlock.lock();
		
		try {
			Path trackpath = DepositHub.DepositStore.getPath();
			
			try {
				Files.createDirectories(trackpath);
			}
			catch (IOException x) {
				Logger.error("Unable to track deposits, error making folder: " + x);
				return false;
			}
			
			RecordStruct lstate = DepositHub.depositstatus.getFieldAsRecord(ApplicationHub.getNodeId());
			
			if (lstate == null) {
				lstate = RecordStruct.record();
				DepositHub.depositstatus.with(ApplicationHub.getNodeId(), lstate);
			}
			
			lstate
					.with("NextId", DepositHub.nextid);
			
			try {
				// TODO review, someday make DB updates more effecient - don't need to write them all
				// choose local or db for state storage
				if (ResourceHub.getResources().getDatabases().hasDefaultDatabase()) {
					IConnectionManager connectionManager = ResourceHub.getResources().getDatabases().getDatabase();
					
					DatabaseAdapter adapter = connectionManager.allocateAdapter();

					for (FieldStruct node : DepositHub.depositstatus.getFields()) {
						for (FieldStruct fld : Struct.objectToRecord(node.getValue()).getFields()) {
							// root - tenant, node id, field name = field value
							adapter.set("root", "dcFileState", node.getName(), fld.getName(), fld.getValue());
						}
					}
					
					return true;
				}
				else {
					Path triggpath = trackpath.resolve("state.json");
					
					if (DepositHub.status == null)
						DepositHub.status = new RandomAccessFile(triggpath.toFile(), "rws");
					
					DepositHub.status.seek(0);
					
					String data = DepositHub.depositstatus.toPrettyString();
					
					/*
					System.out.println("--------------------");
					System.out.println(">>> " + data);
					System.out.println("--------------------");
					*/
					
					DepositHub.status.write(Utf8Encoder.encode(data));
					
					//return IOUtil.saveEntireFile(triggpath, );
					
					return true;
				}
			}
			catch (IOException x) {
				Logger.error("Unable to update deposit status in file: " + x);
				return false;
			}
			catch (DatabaseException x) {
				Logger.error("Unable to update deposit status in database: " + x);
				return false;
			}
		}
		finally {
			DepositHub.depositlock.unlock();
		}
	}
	
	static public boolean loadTracking() {
		Path trackpath = DepositHub.DepositStore.getPath();
		
		try {
			Files.createDirectories(trackpath);
		}
		catch (IOException x) {
			Logger.error("Unable to track deposits, error making folder: " + x);
			return false;
		}
		
		boolean isloaded = false;
		
		// choose local or db for state storage
		if (ResourceHub.getResources().getDatabases().hasDefaultDatabase()) {
			IConnectionManager connectionManager = ResourceHub.getResources().getDatabases().getDatabase();
			
			DatabaseAdapter adapter = connectionManager.allocateAdapter();
			
			try {
				if (adapter.hasAny("root", "dcFileState")) {
					RecordStruct filestate = RecordStruct.record();
					
					byte[] nodeid = adapter.nextPeerKey("root", "dcFileState", null);
					
					while (nodeid != null) {
						Object nid = ByteUtil.extractValue(nodeid);
						
						RecordStruct nodestate = RecordStruct.record();
						
						filestate.with(nid.toString(), nodestate);
						
						byte[] fldname = adapter.nextPeerKey("root", "dcFileState", nid, null);
						
						while (fldname != null) {
							Object fname = ByteUtil.extractValue(fldname);
							Object value = adapter.get("root", "dcFileState", nid, fname);

							nodestate.with(fname.toString(), value);
							
							fldname = adapter.nextPeerKey("root", "dcFileState", nid, fname);
						}
					
						nodeid = adapter.nextPeerKey("root", "dcFileState", nid);
					}
					
					DepositHub.depositstatus = (RecordStruct) filestate;
					
					isloaded = true;
				}
			}
			catch (DatabaseException x) {
				Logger.error("Unable to track deposits, error loading tracker data: " + x);
				return false;
			}
		}
		
		if (! isloaded) {
			Path triggpath = trackpath.resolve("state.json");
			
			if (Files.notExists(triggpath))
				return true;
			
			CompositeStruct res = CompositeParser.parseJson(triggpath);
			
			if ((res == null) || !(res instanceof RecordStruct)) {
				Logger.error("Unable to track deposits, error loading tracker data");
				return false;
			}
			
			DepositHub.depositstatus = (RecordStruct) res;
		}
		
		RecordStruct lstate = DepositHub.depositstatus.getFieldAsRecord(ApplicationHub.getNodeId());
		
		if ((lstate != null) && lstate.isNotFieldEmpty("NextId"))
			DepositHub.nextid.set(lstate.getFieldAsInteger("NextId", 0));
		
		return true;
	}
	
	static public void attemptTrigger(Path trigger) throws OperatingContextException {
		// TODO system messages if anything fails here
		if (! Files.exists(trigger)) {
			Logger.error("Next deposit trigger path does not exist, unable to build: " + trigger);
			return;
		}
		
		// check lock - claim all deposits
		CommonPath claimpath = CommonPath.from("/deposits");
		String claimid = ApplicationHub.makeLocalClaim(claimpath, 5);
		
		if (StringUtil.isEmpty(claimid))
			return;			// someone else has it
		
		BuildDepositWork work = new BuildDepositWork()
				.withStartTrigger(trigger);
		
		Task task = Task.ofSubtask("Create and store Deposit", "DEPOSIT")
				.withTopic(WorkTopic.SYSTEM)	// TODO maybe own topic?
				.withWork(work)
				.withTimeout(5)		// configure timeout for claim and here - reduce once we use async writes
				.withClaimPath(claimpath.toString())
				.withClaimId(claimid);
		
		if (Logger.isDebug())
			Logger.debug("Submit build deposit: " + trigger.getFileName());
		
		TaskHub.submit(task,
				new TaskObserver() {
					@Override
					public void callback(TaskContext taskctx) {
						ApplicationHub.releaseLocalClaim(claimpath, claimid);
					}
				});
	}
	
	static public Path nextDepositOnQueue() {
		String lastdeposit = null;
		
		RecordStruct lstate = DepositHub.depositstatus.getFieldAsRecord(ApplicationHub.getNodeId());
		
		if (lstate != null)
			lastdeposit = lstate.getFieldAsString("LastDeposit");
		
		long lastdepositnum = (lastdeposit == null) ? 0 : StringUtil.parseInt(lastdeposit);
		
		if (lastdeposit != null)
			lastdepositnum++;
		
		if (lastdepositnum >= DepositHub.nextid.get())
			return null;
		
		String trydeposit = StringUtil.leftPad(lastdepositnum + "", 15, '0');
		
		return DepositHub.DepositStore.resolvePath("/triggers/"
				+ ApplicationHub.getNodeId() + "-" + trydeposit + ".json");
	}
	
	// check CloudStore before calling
	static public void attemptRemoteTrigger(String nodeid, Path trigger) throws OperatingContextException {
		// TODO system messages if anything fails here
		if (! Files.exists(trigger)) {
			Logger.error("Next deposit trigger path does not exist, unable to build: " + trigger);
			return;
		}
		
		// check lock - claim all remote deposits
		CommonPath claimpath = CommonPath.from("/remote-deposits");
		String claimid = ApplicationHub.makeLocalClaim(claimpath, 5);
		
		if (StringUtil.isEmpty(claimid))
			return;			// someone else has it
		
		LoadDepositWork work = new LoadDepositWork()
				.withNodeId(nodeid)
				.withStartTrigger(trigger);
		
		Task task = Task.ofSubtask("Create and store Deposit", "DEPOSIT")
				.withTopic(WorkTopic.SYSTEM)	// TODO maybe own topic?
				.withWork(work)
				.withTimeout(5)		// configure timeout for claim and here - reduce once we use async writes
				.withClaimPath(claimpath.toString())
				.withClaimId(claimid);
		
		if (Logger.isDebug())
			Logger.debug("Submit build deposit: " + trigger.getFileName());
		
		TaskHub.submit(task,
				new TaskObserver() {
					@Override
					public void callback(TaskContext taskctx) {
						ApplicationHub.releaseLocalClaim(claimpath, claimid);
					}
				});
	}
	
	// these two maybe should lock - though really only ever should be called safely
	static public String nextRemoteDeposit(String nodeid) {
		String lastdeposit = null;
		
		RecordStruct lstate = DepositHub.depositstatus.getFieldAsRecord(nodeid);
		
		if (lstate != null)
			lastdeposit = lstate.getFieldAsString("LastChain");
		
		long lastdepositnum = (lastdeposit == null) ? 0 : StringUtil.parseInt(lastdeposit);
		
		if (lastdeposit != null)
			lastdepositnum++;
		
		return StringUtil.leftPad(lastdepositnum + "", 15, '0');
	}
	
	static public Path nextRemoteDepositOnQueue(String nodeid) {
		String lastdeposit = null;
		
		RecordStruct lstate = DepositHub.depositstatus.getFieldAsRecord(nodeid);
		
		if (lstate != null)
			lastdeposit = lstate.getFieldAsString("LastDeposit");
		
		long lastdepositnum = (lastdeposit == null) ? 0 : StringUtil.parseInt(lastdeposit);
		
		if (lastdeposit != null)
			lastdepositnum++;
		
		String trydeposit = StringUtil.leftPad(lastdepositnum + "", 15, '0');
		
		Path tpath = DepositHub.DepositStore.resolvePath("/triggers/"
				+ nodeid + "-" + trydeposit + ".json");
		
		if (Files.exists(tpath))
			return tpath;
		
		return null;
	}
	
	static public AwsStore getCloudStore() {
		XElement depositsettings = ApplicationHub.getCatalogSettings("Deposit-Storage");
		
		if (depositsettings == null) {
			//System.out.println("Missing settings Interchange-Aws");
			return null;
		}
		
		String catalog = depositsettings.getAttribute("Catalog");
		String path = depositsettings.getAttribute("Path");
		
		if (StringUtil.isEmpty(catalog) || StringUtil.isEmpty(path))
			return null;
		
		CommonPath remotepath = CommonPath.from(path);
		
		if (remotepath == null) {
			//System.out.println("bad path");
			return null;
		}
		
		XElement settings = ApplicationHub.getCatalogSettings(catalog);
		
		if (settings == null) {
			//System.out.println("Missing settings Interchange-Aws");
			return null;
		}
		
		// TODO improve settings - get Service and DataType from depositsettings
		// TODO create appropriate remote store object, not just AWS
		// TODO set region
		
		return AwsStore.of(settings.getAttribute("KeyId"), settings.getAttribute("SecretKey"), remotepath);
	}
	
	static public class PeriodicTriggerReviewWork implements IWork {
		@Override
		public void run(TaskContext taskctx) throws OperatingContextException {
			Path triggpath = DepositHub.nextDepositOnQueue();
			
			Logger.trace("Check deposit triggers: " + triggpath);
			
			if (triggpath != null)
				DepositHub.attemptTrigger(triggpath);
			
			taskctx.returnEmpty();
		}
	}
	
	static public class PeriodicRemoteWork implements IWork {
		@Override
		public void run(TaskContext taskctx) throws OperatingContextException {
			AwsStore store = DepositHub.getCloudStore();
			
			if (store != null) {
				// TODO improve, this currently checks only 1 file per pass - could get all new per pass
				String remotenodeid = ApplicationHub.getNodeId().equals("00110") ? "00210" : "00110";  // TODO configure list of remote nodes
				
				String nextid = DepositHub.nextRemoteDeposit(remotenodeid);
				
				store.getFileDetail(CommonPath.from("/deposits/" + remotenodeid + "/chain/" + nextid + ".chain"),
						new OperationOutcome<FileStoreFile>() {
							@Override
							public void callback(FileStoreFile result) throws OperatingContextException {
								if ((result != null) && result.exists()) {
									RecordStruct trec = RecordStruct.record()
											.with("DepositId", nextid);
									
									// allocate (above) ensures folder exists
									Path triggpath = DepositHub.DepositStore.resolvePath("/triggers/"
											+ remotenodeid + "-" + nextid + ".json");
									
									IOUtil.saveEntireFile(triggpath, trec.toPrettyString());
									
									DepositHub.clearLastChain(remotenodeid, nextid);
								}
								
								Path triggpath = DepositHub.nextRemoteDepositOnQueue(remotenodeid);
								
								// TODO switch to trace
								Logger.info("Check remote deposit triggers: " + triggpath);
								
								if (triggpath != null)
									DepositHub.attemptRemoteTrigger(remotenodeid, triggpath);
							}
						});
			
	
				/* TODO marker not quite working
				AWSAuthConnection connection = store.getConnection();
				
				Logger.info("check remote: ");
				
				try {
					dcraft.aws.s3.ListBucketResponse x = connection.listBucket(store.getBucket(),
							depositfolder.resolveToKey() + "/", "000000000000001.chain", 3, null);
					
					int code = x.connection.getResponseCode();
					
					Logger.debug("AWS folder listing, response: " + code);
					
					Logger.info("marker: " + x.nextMarker);
					
					if ((x.entries != null)) {
						for (ListEntry e : x.entries) {
							Logger.info("found: " + e.key);
							
							/ *
							RecordStruct rec = RecordStruct.record();
							
							// trim the path down so it is relative to the Root of the Store
							rec
									.with("Path", e.key.substring(this.awsdriver.getRootFolder().length() - 1))
									.with("Modified", e.lastModified)    // will be seen as TemporalAccessor
									.with("Size", e.size)
									.with("AwsETag", e.eTag)
									.with("IsFolder", false)
									.with("Exists", true);
									* /
						}
					}
				}
				catch (IOException x) {
					Logger.error("Unable to write the text: " + x);
				}
				*/
				
			}
			
			taskctx.returnEmpty();
		}
	}
}
