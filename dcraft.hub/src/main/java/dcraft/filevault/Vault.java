package dcraft.filevault;

import dcraft.db.BasicRequestContext;
import dcraft.db.IConnectionManager;
import dcraft.db.fileindex.FileIndexAdapter;
import dcraft.filestore.FileDescriptor;
import dcraft.hub.ResourceHub;
import dcraft.hub.op.*;
import dcraft.session.Session;
import dcraft.stream.StreamFragment;
import dcraft.struct.ListStruct;
import dcraft.task.IWork;
import dcraft.tenant.Site;
import dcraft.tenant.Tenant;
import dcraft.tenant.TenantHub;

import java.util.HashMap;
import java.util.List;

import dcraft.filestore.CommonPath;
import dcraft.struct.RecordStruct;
import dcraft.struct.Struct;
import dcraft.util.StringUtil;
import dcraft.xml.XElement;

abstract public class Vault {
	static public Vault of(Site tenancy, XElement vaultdef) throws OperatingContextException {
		Vault b = (Vault) OperationContext.getOrThrow().getResources().getClassLoader().getInstance(vaultdef.getAttribute("VaultClass"));

		b.init(tenancy, vaultdef, null);

		return b;
	}

	protected String name = null;
	protected String bestEvidence = null;
	protected String minEvidence = null;
	protected String[] readauthlist = null;
	protected String[] writeauthlist = null;
	protected boolean uploadtoken = false;
	
	protected String tenant = null;
	protected String site = null;
	
	public Site getSite() {
		Tenant tenant = TenantHub.resolveTenant(this.tenant);
		
		if (tenant == null)
			return null;
		
		return tenant.resolveSite(this.site);
	}
	
	/* groovy
	protected GroovyObject script = null;
	*/
	
	public void init(Site di, XElement bel, OperationOutcomeEmpty cb) throws OperatingContextException {
		this.name = bel.getAttribute("Id");
		
		this.tenant = di.getTenant().getAlias();
		this.site = di.getAlias();
		
		/* TODO restore groovy support - where to put scriptold?
		Path bpath = di.resolvePath("buckets").resolve(bname + ".groovy");
		
		if (Files.exists(bpath)) {
			try {
				Class<?> groovyClass = di.getScriptLoader().toClass(bpath);
				
				this.scriptold = (GroovyObject) groovyClass.newInstance();
				
				this.tryExecuteMethod("Init", new Object[] { di });
			}
			catch (Exception x) {
				Logger.error("Unable to prepare bucket scriptold: " + bpath);
				Logger.error("Error: " + x);
			}
		}
		*/
		
		String ratags = bel.getAttribute("ReadBadges");
		
		if (StringUtil.isNotEmpty(ratags)) 
			 this.readauthlist = ratags.split(",");
		
		String watags = bel.getAttribute("WriteBadges");
		
		if (StringUtil.isNotEmpty(watags)) 
			 this.writeauthlist = watags.split(",");
		
		this.uploadtoken = Struct.objectToBoolean(bel.getAttribute("UploadToken", "False"));
		
		this.bestEvidence = bel.getAttribute("BestEvidence", "SHA256");
		this.minEvidence = bel.getAttribute("MinEvidence","Size");
	}
	
	public String getName() {
		return this.name;
	}
	
	public String getBestEvidence() {
		return this.bestEvidence;
	}
	
	public String getMinEvidence() {
		return this.minEvidence;
	}
	
	public boolean isUploadtokenRequired() {
		return this.uploadtoken;
	}
	
	public boolean checkReadAccess(String op, String path, RecordStruct params) throws OperatingContextException {
		UserContext uctx = OperationContext.getOrThrow().getUserContext();
		
		if (this.readauthlist == null)
			return ! uctx.looksLikeGuest();
		
		return uctx.isTagged(this.readauthlist);
	}
	
	public boolean checkWriteAccess(String op, String path, RecordStruct params) throws OperatingContextException {
		UserContext uctx = OperationContext.getOrThrow().getUserContext();
		
		if (this.writeauthlist == null)
			return ! uctx.looksLikeGuest();
		
		return uctx.isTagged(this.writeauthlist);
	}
	
	// defaults to write access
	public boolean checkCustomAccess(String cmd, String path, RecordStruct params) throws OperatingContextException {
		return this.checkWriteAccess("Custom", path, params);
	}
	
	public boolean checkUploadToken(RecordStruct data, FileDescriptor file) throws OperatingContextException {
		if (!this.uploadtoken)
			return true;
		
		String token = data.getFieldAsString("Token");

		Session session = OperationContext.getOrThrow().getSession();

		if (session == null)
			return false;

		HashMap<String, Struct> scache = session.getCache();
		
		if (! scache.containsKey(token))
			return false;
		
		String path = data.getFieldAsString("Path");
		
		if (StringUtil.isEmpty(path))
			return true;
		
		if (StringUtil.isNotEmpty(path) && path.contains(token))
			return true;
		
		return false;
	}

	public String getTxForToken(RecordStruct data) throws OperatingContextException {
		String token = data.getFieldAsString("Token");

		return VaultUtil.getSessionTokenTx(token);
	}

	public void clearToken(RecordStruct data) throws OperatingContextException {
		String token = data.getFieldAsString("Token");

		VaultUtil.clearSessionToken(token);
	}

	// TODO should use a callback approach
	public void beforeSubmitTransaction(TransactionBase tx) throws OperatingContextException {
		// nothing to do
	}
	
	public void processTransaction(TransactionBase tx) throws OperatingContextException {
		IConnectionManager connectionManager = ResourceHub.getResources().getDatabases().getDatabase();
		
		FileIndexAdapter adapter = FileIndexAdapter.of(BasicRequestContext.of(connectionManager.allocateAdapter()));
		
		for (CommonPath file : tx.getDeletelist()) {
			adapter.deleteFile(
					this,
					file,
					tx.getTimestamp(),
					this.buildHistory(tx, "Delete")
			);
		}
		
		CommonPath cleanup = tx.getCleanFolder();
		
		if (cleanup != null)
			adapter.deleteFile(this, cleanup, tx.getTimestamp(), this.buildHistory(tx, "Clean"));
		
		for (CommonPath file : tx.getUpdateList()) {
			adapter.indexFile(
					this,
					file,
					tx.getTimestamp(),
					this.buildHistory(tx, "Write")
			);
		}
	}

	public RecordStruct buildHistory(TransactionBase tx, String op) {
		if (StringUtil.isNotEmpty(tx.getDepositId()))
			return RecordStruct.record()
					.with("Source", "Deposit")
					.with("Deposit", tx.getDepositId())
					.with("Op", op)
					.with("TimeStamp", tx.getTimestamp())
					.with("Node", tx.getNodeId());
				
		return RecordStruct.record()
				.with("Source", "Scan")
				.with("Op", op)
				.with("TimeStamp", tx.getTimestamp())
				.with("Node", tx.getNodeId());
	}
	
	public IWork buildIndexWork() {
		return null;
	}
	
	public void afterDeposit(RecordStruct manifest) throws OperatingContextException { }
	
	/*
	 * ================ programming points ==================
	 */
	
	// return true if executed something
	public boolean tryExecuteMethod(String name, Object... params) {
	/* groovy
		if (this.script == null)
			return false;
		
		Method runmeth = null;
		
		for (Method m : this.script.getClass().getMethods()) {
			if (! m.getName().equals(name))
				continue;
			
			runmeth = m;
			break;
		}
		
		if (runmeth == null)
			return false;
		
		try {
			this.script.invokeMethod(name, params);
			
			return true;
		}
		catch (Exception x) {
			Logger.error("Unable to execute watcher scriptold!");
			Logger.error("Error: " + x);
		}
		*/
		
		return false;
	}
	
	public void executeCustom(RecordStruct request, OperationOutcomeStruct fcb) throws OperatingContextException {
		RecordStruct resp = RecordStruct.record();
		
		if (this.tryExecuteMethod("Custom", request, resp, fcb))
			return;
		
		fcb.returnValue(resp);
	}

	public void beforeStartDownload(FileDescriptor fi, RecordStruct params, OperationOutcome<FileDescriptor> cb) {
		if (this.tryExecuteMethod("BeforeStartDownload", fi, params, cb))
			return;
		
		cb.returnValue(fi);
	}

	public void afterStartDownload(FileDescriptor fi, RecordStruct resp, OperationOutcomeStruct cb) {
		if (this.tryExecuteMethod("AfterStartDownload", fi, resp, cb))
			return;
		
		cb.returnValue(resp);
	}

	public void finishDownload(FileDescriptor fi, RecordStruct data, RecordStruct extra, boolean pass, String evidenceUsed, OperationOutcomeStruct cb) {
		if (this.tryExecuteMethod("FinishDownload", fi, data, extra, pass, evidenceUsed, cb))
			return;
		
		cb.returnValue(RecordStruct.record().with("Extra", extra));
	}

	public void beforeStartUpload(FileDescriptor fi, RecordStruct params, OperationOutcome<FileDescriptor> cb) {
		if (this.tryExecuteMethod("BeforeStartUpload", fi, params, cb))
			return;
		
		cb.returnValue(fi);
	}

	public void afterStartUpload(FileDescriptor fi, RecordStruct resp, OperationOutcomeStruct cb) {
		if (this.tryExecuteMethod("AfterStartUpload", fi, resp, cb))
			return;
		
		cb.returnValue(resp);
	}

	public void finishUpload(FileDescriptor fi, RecordStruct extra, boolean pass, String evidenceUsed, OperationOutcomeStruct cb) {
		if (this.tryExecuteMethod("FinishUpload", fi, extra, pass, evidenceUsed, cb))
			return;
		
		cb.returnValue(RecordStruct.record().with("Extra", extra));
	}

	protected void beforeRemove(FileDescriptor fi, RecordStruct params, OperationOutcomeEmpty cb) {
		if (this.tryExecuteMethod("BeforeRemove", fi, params, cb))
			return;
		
		cb.returnEmpty();
	}

	protected void afterRemove(FileDescriptor fi, RecordStruct params, OperationOutcomeEmpty cb) {
		if (this.tryExecuteMethod("AfterRemove", fi, params, cb))
			return;
		
		cb.returnEmpty();
	}

	protected void beforeRename(FileDescriptor fi, ListStruct files, RecordStruct params, OperationOutcomeEmpty cb) {
		if (this.tryExecuteMethod("BeforeRename", fi, files, params, cb))
			return;

		cb.returnEmpty();
	}

	protected void afterRename(FileDescriptor fi, ListStruct files, RecordStruct params, OperationOutcomeEmpty cb) {
		if (this.tryExecuteMethod("AfterRename", fi, files, params, cb))
			return;

		cb.returnEmpty();
	}

	protected void beforeMove(FileDescriptor source, FileDescriptor dest, RecordStruct params, OperationOutcomeEmpty cb) {
		if (this.tryExecuteMethod("BeforeMove", source, dest, params, cb))
			return;

		cb.returnEmpty();
	}

	protected void afterMove(FileDescriptor source, FileDescriptor dest, RecordStruct params, OperationOutcomeEmpty cb) {
		if (this.tryExecuteMethod("AfterMove", source, dest, params, cb))
			return;

		cb.returnEmpty();
	}

	/*
	 * ================ features ==================
	 */
	
	// add more methods for mapping (delete, hash, add folder)
	
	public void getMappedFileDetail(String path, RecordStruct params, OperationOutcome<FileDescriptor> fcb) throws OperatingContextException {
		if (this.tryExecuteMethod("MapRequest", path, params, fcb))
			return;
		
		this.getFileDetail(new CommonPath(path), params, fcb);
	}
	
	// these assume path already mapped
	
	abstract public void getFileDetail(CommonPath path, RecordStruct params, OperationOutcome<FileDescriptor> fcb) throws OperatingContextException;
	
	abstract public void addFolder(CommonPath path, RecordStruct params, OperationOutcome<FileDescriptor> callback) throws OperatingContextException;

	abstract public void getFolderListing(FileDescriptor file, RecordStruct params, OperationOutcome<List<? extends FileDescriptor>> callback) throws OperatingContextException;
	
	abstract public void deleteFiles(List<FileDescriptor> files, RecordStruct params, OperationOutcomeEmpty callback) throws OperatingContextException;

	abstract public void moveFile(FileDescriptor file, FileDescriptor dest, RecordStruct params, OperationOutcomeEmpty callback) throws OperatingContextException;

	abstract public StreamFragment toSourceStream(FileDescriptor fileDescriptor) throws OperatingContextException;
	
	abstract public void hashFile(FileDescriptor fileDescriptor, String evidence, RecordStruct params, OperationOutcomeString callback) throws OperatingContextException;
	
	abstract public Transaction buildUpdateTransaction(String txid, RecordStruct params);
}
