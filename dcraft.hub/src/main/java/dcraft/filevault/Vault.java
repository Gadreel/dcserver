package dcraft.filevault;

import dcraft.filestore.FileStoreFile;
import dcraft.filestore.local.LocalStoreFile;
import dcraft.hub.op.*;
import dcraft.stream.StreamFragment;
import dcraft.tenant.Base;
import dcraft.util.RndUtil;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.function.Consumer;

import dcraft.filestore.CommonPath;
import dcraft.filestore.FileStore;
import dcraft.filestore.local.LocalStore;
import dcraft.log.Logger;
import dcraft.struct.FieldStruct;
import dcraft.struct.ListStruct;
import dcraft.struct.RecordStruct;
import dcraft.struct.Struct;
import dcraft.struct.scalar.BooleanStruct;
import dcraft.util.StringUtil;
import dcraft.xml.XElement;

public class Vault {
	protected String name = null;
	protected FileStore fsd = null;
	protected VaultMode mode = VaultMode.Expand;		// TODO currently no support for any of the SHared modes
	protected String bestEvidence = null;
	protected String minEvidence = null;
	protected String[] readauthlist = null;
	protected String[] writeauthlist = null;
	protected boolean uploadtoken = false;
	
	/* groovy
	protected GroovyObject script = null;
	*/
	
	public void init(Base di, XElement bel, OperationOutcomeEmpty cb) {
		this.name = bel.getAttribute("Id");
		
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
		
		if (bel.hasNotEmptyAttribute("Mode"))
			this.mode = VaultMode.valueOf(bel.getAttribute("Mode"));
		
		String ratags = bel.getAttribute("ReadBadges");
		
		if (StringUtil.isNotEmpty(ratags)) 
			 this.readauthlist = ratags.split(",");
		
		String watags = bel.getAttribute("WriteBadges");
		
		if (StringUtil.isNotEmpty(watags)) 
			 this.writeauthlist = watags.split(",");
		
		this.uploadtoken = Struct.objectToBoolean(bel.getAttribute("UploadToken", "False"));
		
		this.bestEvidence = bel.getAttribute("BestEvidence", "SHA256");
		this.minEvidence = bel.getAttribute("MinEvidence","Size");
		
		String root = bel.hasNotEmptyAttribute("DirectPath")
				? bel.getAttribute("DirectPath")
				: di.resolvePath(bel.getAttribute("RootFolder", "./vault/" + this.name)).toAbsolutePath().normalize().toString();

		RecordStruct cparams = new RecordStruct().with("RootFolder", root);
		
		// TODO enhance, someday this doesn't have to be a local FS
		this.fsd = new LocalStore();
		
		this.fsd.connect(cparams, cb);
	}
	
	public String getName() {
		return this.name;
	}
	
	public VaultMode getMode() {
		return this.mode;
	}
	
	public String getBestEvidence() {
		return this.bestEvidence;
	}
	
	public String getMinEvidence() {
		return this.minEvidence;
	}
	
	public boolean checkReadAccess() throws OperatingContextException {
		UserContext uctx = OperationContext.getOrThrow().getUserContext();
		
		if (this.readauthlist == null)
			return ! uctx.looksLikeGuest();
		
		return uctx.isTagged(this.readauthlist);
	}
	
	public boolean checkWriteAccess() throws OperatingContextException {
		UserContext uctx = OperationContext.getOrThrow().getUserContext();
		
		if (this.writeauthlist == null)
			return ! uctx.looksLikeGuest();
		
		return uctx.isTagged(this.writeauthlist);
	}
	
	public boolean checkUploadToken(RecordStruct data, FileStoreFile file) throws OperatingContextException {
		if (!this.uploadtoken)
			return true;
		
		String token = data.getFieldAsString("Token");
		
		HashMap<String, Struct> scache = OperationContext.getOrThrow().getSession().getCache();
		
		if (! scache.containsKey(token))
			return false;
		
		String path = data.getFieldAsString("Path");
		
		if (StringUtil.isEmpty(path))
			return true;
		
		if (StringUtil.isNotEmpty(path) && path.contains(token))
			return true;
		
		return false;
	}
	
	public FileStore getFileStore() {
		return this.fsd;
	}
	
	/*
	 * ================ programming points ==================
	 */
	
	// return true if executed something
	protected boolean tryExecuteMethod(String name, Object... params) {
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
	
	// feedback
	// - a file  (file returned may have IsReadable and IsWritable set to indicate permissions for current context)
	// - log errors
	protected void mapRequest(RecordStruct data, OperationOutcome<FileStoreFile> fcb) {
		if (this.tryExecuteMethod("MapRequest", this, data, fcb))
			return;
		
		String path = data.getFieldAsString("Path");
		
		this.fsd.getFileDetail(new CommonPath(path), fcb);
	}

	// feedback
	// - lister is optional - it can filter entries and embellish entries
	// - log errors
	// - provide Extra response
	protected void getLister(FileStoreFile fi, RecordStruct data, OperationOutcome<VaultLister> fcb) {
		// TODO
	}

	// feedback
	// - replace a file with another 
	// - log errors
	// - provide Extra response
	protected void beforeStartDownload(FileStoreFile fi, RecordStruct data, RecordStruct extra, OperationOutcome<FileStoreFile> fcb) {
		if (this.tryExecuteMethod("BeforeStartDownload", this, fi, data, extra, fcb))
			return;
		
		fcb.returnValue(fi);
	}

	// feedback
	// - log errors
	// - provide Extra response
	protected void afterStartDownload(FileStoreFile fi, RecordStruct data, RecordStruct resp, OperationOutcomeStruct cb) {
		if (this.tryExecuteMethod("AfterStartDownload", this, fi, data, resp, cb))
			return;
		
		cb.returnValue(resp);
	}

	// feedback
	// - log errors
	// - provide Extra response
	protected void finishDownload(FileStoreFile fi, RecordStruct data, RecordStruct extra, boolean pass, String evidenceUsed, OperationOutcomeStruct cb) {
		if (this.tryExecuteMethod("FinishDownload", this, fi, data, extra, pass, evidenceUsed, cb))
			return;
		
		cb.returnValue(extra);
	}

	// feedback
	// - replace a file with another 
	// - log errors
	// - provide Extra response
	protected void beforeStartUpload(FileStoreFile fi, RecordStruct data, RecordStruct extra, OperationOutcome<FileStoreFile> fcb) {
		if (this.tryExecuteMethod("BeforeStartUpload", this, fi, data, extra, fcb))
			return;
		
		fcb.returnValue(fi);
	}

	// feedback
	// - log errors
	// - provide Extra response
	protected void afterStartUpload(FileStoreFile fi, RecordStruct data, RecordStruct resp, OperationOutcomeStruct cb) {
		if (this.tryExecuteMethod("AfterStartUpload", this, fi, data, resp, cb))
			return;
		
		cb.returnValue(resp);
	}

	// feedback
	// - log errors
	// - provide Extra response
	protected void finishUpload(FileStoreFile fi, RecordStruct data, RecordStruct extra, boolean pass, String evidenceUsed, OperationOutcomeStruct cb) {
		// TODO consider the Watch triggers
		//
		//if (! fcb.hasErrors())
		//	Vault.this.watch("Upload", fi);
		
		if (this.tryExecuteMethod("FinishUpload", this, fi, data, extra, pass, evidenceUsed, cb))
			return;
		
		cb.returnValue(extra);
	}

	// feedback
	// - log errors
	// - provide Extra response
	protected void beforeRemove(FileStoreFile fi, RecordStruct data, RecordStruct extra, OperationOutcomeEmpty cb) {
		if (this.tryExecuteMethod("BeforeRemove", this, fi, data, extra, cb))
			return;
		
		cb.returnResult();
	}

	// feedback
	// - log errors
	// - provide Extra response
	protected void afterRemove(FileStoreFile fi, RecordStruct data, RecordStruct extra, OperationOutcomeStruct cb) {
		if (this.tryExecuteMethod("AfterRemove", this, fi, data, extra, cb))
			return;
		
		cb.returnValue(extra);
	}
	
	protected String createTransactionId(RecordStruct data, RecordStruct extra) {
		return Transaction.createTransactionId();  // token is protected by session - session id is secure random
	}
	
	/*
	 * ================ features ==================
	 */

	public void getFileDetail(RecordStruct request, boolean checkAuth, OperationOutcomeStruct fcb) throws OperatingContextException {
		try {
			// check bucket security
			if (checkAuth && ! this.checkReadAccess()) {
				Logger.errorTr(434);
				fcb.returnEmpty();
				return;
			}
			
			this.mapRequest(request, new OperationOutcome<FileStoreFile>() {
				@Override
				public void callback(FileStoreFile result) throws OperatingContextException {
					if (this.hasErrors()) {
						fcb.returnEmpty();
						return;
					}
					
					if (this.isEmptyResult()) {
						Logger.error("Your request appears valid but does not map to a file.  Unable to complete.");
						fcb.returnEmpty();
						return;
					}
					
					FileStoreFile fi = this.getResult();
					
					if (!fi.exists()) {
						Logger.error("File does not exist");
						fcb.returnEmpty();
						return;
					}
					
					RecordStruct fdata = new RecordStruct();
					
					fdata.with("FileName", fi.getName());
					fdata.with("IsFolder", fi.isFolder());
					fdata.with("Modified", fi.getModificationAsTime());
					fdata.with("Size", fi.getSize());
					fdata.with("Extra", fi.getExtra());
					
					String meth = request.getFieldAsString("Method");
					
					if (StringUtil.isEmpty(meth) || fi.isFolder()) {
						fcb.returnValue(fdata);
						return;
					}
			
					fi.hash(meth, new OperationOutcome<String>() {						
						@Override
						public void callback(String result) throws OperatingContextException {
							if (! fcb.hasErrors()) {
								fdata.with("Hash", result);
								fcb.returnValue(fdata);
							}
							else {
								fcb.returnEmpty();
							}
						}
					});
				}
			});
		} 
		catch (OperatingContextException x) {
			Logger.error("Operating context error: " + x);
			fcb.returnEmpty();
		}
	}
	
	public void deleteFile(RecordStruct request, boolean checkAuth, OperationOutcomeStruct fcb) throws OperatingContextException {
		try {
			// check bucket security
			if (checkAuth && ! this.checkWriteAccess()) {
				Logger.errorTr(434);
				fcb.returnValue(null);
				return;
			}
			
			this.mapRequest(request, new OperationOutcome<FileStoreFile>() {
				@Override
				public void callback(FileStoreFile result) throws OperatingContextException {
					if (this.hasErrors()) {
						fcb.returnEmpty();
						return;
					}
					
					if (this.isEmptyResult()) {
						Logger.error("Your request appears valid but does not map to a file.  Unable to complete.");
						fcb.returnEmpty();
						return;
					}
					
					FileStoreFile fi = this.getResult();
					RecordStruct extra = new RecordStruct();
					
					Vault.this.beforeRemove(fi, request, extra, new OperationOutcomeEmpty() {
						@Override
						public void callback() throws OperatingContextException {
							if (this.hasErrors() || ! fi.exists()) {
								fcb.returnValue(extra);
								return;
							}

							fi.remove(new OperationOutcomeEmpty() {					
								@Override
								public void callback() {
									Vault.this.afterRemove(fi, request, extra, fcb);
								}
							});
						}
					});
				}
			});
		} 
		catch (OperatingContextException x) {
			Logger.error("Operating context error: " + x);
			fcb.returnEmpty();
		}
	}
	
	public void addFolder(RecordStruct request, boolean checkAuth, OperationOutcomeStruct fcb) throws OperatingContextException {
		try {
			// check bucket security
			if (checkAuth && ! this.checkWriteAccess()) {
				Logger.errorTr(434);
				fcb.returnResult();
				return;
			}
			
			this.mapRequest(request, new OperationOutcome<FileStoreFile>() {
				@Override
				public void callback(FileStoreFile result) throws OperatingContextException {
					if (this.hasErrors()) {
						fcb.returnResult();
						return;
					}
					
					if (this.isEmptyResult()) {
						Logger.error("Your request appears valid but does not map to a file.  Unable to complete.");
						fcb.returnResult();
						return;
					}
					
					FileStoreFile fi = this.getResult();
					
					if (fi.exists() && fi.isFolder()) {
						fcb.returnResult();
						return;
					}
					
					if (fi.exists() && !fi.isFolder()) {
						Logger.error("Path already maps to a file, unable to create folder");
						fcb.returnResult();
						return;
					}

					Vault.this.fsd.addFolder(fi.getPathAsCommon(), new OperationOutcome<FileStoreFile>() {
						@Override
						public void callback(FileStoreFile result) throws OperatingContextException {
							fcb.returnResult();
						}
					});
				}
			});
		} 
		catch (OperatingContextException x) {
			Logger.error("Operating context error: " + x);
			fcb.returnEmpty();
		}
	}
	
	// TODO test this, somehow FullPath was returned to call (or in an error maybe)
	public void listFiles(RecordStruct request, boolean checkAuth, OperationOutcomeStruct fcb) throws OperatingContextException {
		try {
			// check bucket security
			if (checkAuth && ! this.checkReadAccess()) {
				Logger.errorTr(434);
				fcb.returnEmpty();
				return;
			}
			
			this.mapRequest(request, new OperationOutcome<FileStoreFile>() {
				@Override
				public void callback(FileStoreFile result) throws OperatingContextException {
				if (this.hasErrors()) {
					fcb.returnEmpty();
					return;
				}

				if (this.isEmptyResult()) {
					Logger.error("Your request appears valid but does not map to a file.  Unable to complete.");
					fcb.returnEmpty();
					return;
				}

				FileStoreFile fi = this.getResult();

				if (!fi.exists()) {
					fcb.returnEmpty();
					return;
				}

				Vault.this.fsd.getFolderListing(fi.getPathAsCommon(), new OperationOutcome<List<FileStoreFile>>() {
					@Override
					public void callback(List<FileStoreFile> result) throws OperatingContextException {
						if (this.hasErrors()) {
							fcb.returnValue(null);
							return;
						}

						boolean showHidden = OperationContext.getOrThrow().getUserContext().isTagged("Admin");

						ListStruct files = new ListStruct();

						for (FileStoreFile file : this.getResult()) {
							if (file.getName().equals(".DS_Store"))
								continue;

							if (!showHidden && file.getName().startsWith("."))
								continue;

							RecordStruct fdata = new RecordStruct();

							fdata.with("FileName", file.getName());
							fdata.with("IsFolder", file.isFolder());
							fdata.with("Modified", file.getModificationAsTime());
							fdata.with("Size", file.getSize());
							fdata.with("Extra", file.getExtra());

							files.withItem(fdata);
						}

						fcb.returnValue(files);
					}
				});
				}
			});
		} 
		catch (OperatingContextException x) {
			Logger.error("Operating context error: " + x);
			fcb.returnEmpty();
		}
	}
	
	public void executeCustom(RecordStruct request, boolean checkAuth, OperationOutcomeStruct fcb) throws OperatingContextException {
		try {
			// check bucket security
			if (checkAuth && ! this.checkWriteAccess()) {
				Logger.errorTr(434);
				fcb.returnEmpty();
				return;
			}
			
			RecordStruct resp = new RecordStruct();
			fcb.setResult(resp);
			
			if (this.tryExecuteMethod("Custom", this, request, resp, fcb))
				return;
			
			fcb.returnValue(resp);
		} 
		catch (OperatingContextException x) {
			Logger.error("Operating context error: " + x);
			fcb.returnEmpty();
		}
	}

	public void allocateUploadToken(RecordStruct request, boolean checkAuth, OperationOutcomeStruct fcb) throws OperatingContextException {
		RecordStruct resp = new RecordStruct();
		fcb.setResult(resp);
		
		if (this.tryExecuteMethod("AllocateUploadToken", this, request, resp, fcb))
			return;
		
		try {
			String token = RndUtil.nextUUId();  // token is protected by session - session id is secure random
			
			HashMap<String, Struct> scache = OperationContext.getOrThrow().getSession().getCache();
			
			scache.put(token, BooleanStruct.of(true));
			
			resp.with("Token", token);
			
			fcb.returnValue(resp);
		} 
		catch (OperatingContextException x) {
			Logger.error("Operating context error: " + x);
			fcb.returnEmpty();
		}
	}
	
	public void beginTransaction(RecordStruct request, boolean checkAuth, OperationOutcomeStruct fcb) throws OperatingContextException {
		RecordStruct extra = RecordStruct.record();

		fcb.returnValue(RecordStruct.record()
				.with("TransactionId", this.createTransactionId(request, extra))
				.with("Extra", extra)
		);
	}
	
	public void commitTransaction(RecordStruct request, boolean checkAuth, OperationOutcomeStruct fcb) throws OperatingContextException {
		Transaction tx = Transaction.of(request.getFieldAsString("TransactionId"), this);
		
		tx.commitTransaction(null, new OperationOutcomeEmpty() {
			@Override
			public void callback() throws OperatingContextException {
				fcb.returnEmpty();
			}
		});
	}
	
	public void rollbackTransaction(RecordStruct request, boolean checkAuth, OperationOutcomeStruct fcb) throws OperatingContextException {
		Transaction tx = Transaction.of(request.getFieldAsString("TransactionId"), this);
		
		tx.rollbackTransaction(new OperationOutcomeEmpty() {
			@Override
			public void callback() throws OperatingContextException {
				fcb.returnEmpty();
			}
		});
	}
	
	public void startUpload(RecordStruct request, boolean checkAuth, OperationOutcomeStruct fcb) throws OperatingContextException {
		try {
			// check bucket security
			if (checkAuth && ! this.checkWriteAccess() && ! this.uploadtoken) {
				Logger.errorTr(434);
				fcb.returnEmpty();
				return;
			}
			
			this.mapRequest(request, new OperationOutcome<FileStoreFile>() {
				@Override
				public void callback(FileStoreFile result) throws OperatingContextException {
					if (this.hasErrors()) {
						fcb.returnEmpty();
						return;
					}
					
					if (this.isEmptyResult()) {
						Logger.error("Your request appears valid but does not map to a file.  Unable to complete.");
						fcb.returnEmpty();
						return;
					}
					
					FileStoreFile fi = this.getResult();
					
					if (! Vault.this.checkUploadToken(request, fi)) {
						Logger.error("You may not upload to this path.");
						fcb.returnEmpty();
						return;
					}
					
					RecordStruct extra = RecordStruct.record();
					
					String transactionId = request.getFieldAsString("TransactionId");
					TxMode depmode = TxMode.Manual;
					
					if (StringUtil.isEmpty(transactionId)) {
						transactionId = Vault.this.createTransactionId(request, extra);  // token is protected by session - session id is secure random
						depmode = TxMode.Automatic;
					}
					
					String ftransactionId = transactionId;
					TxMode fdepmode = depmode;
					
					Vault.this.beforeStartUpload(fi, request, extra, new OperationOutcome<FileStoreFile>() {
						@Override
						public void callback(FileStoreFile result) throws OperatingContextException {
							if (this.hasErrors()) {
								fcb.returnEmpty();
								return;
							}
							
							HashMap<String, Struct> scache = OperationContext.getOrThrow().getSession().getCache();
							
							String channel = RndUtil.nextUUId();  // token is protected by session - session id is secure random
							
							/* 	TODO handle resume and progress
								TODO what to do with this info?
									.with("TransactionId", ftransactionId)
									.with("Overwrite", request.getFieldAsBooleanOrFalse("Overwrite"))
									.with("Size", request.getFieldAsInteger("Size"));

								boolean forceover = request.getFieldAsBooleanOrFalse("Overwrite");
								long size = request.getFieldAsInteger("Size", 0);
								boolean resume = !forceover && fi.exists();
							*/
					
							// create a root relative to the transaction id in deposits folder
							LocalStore dstore = LocalStore.of(DepositHub.DepositStore.resolvePath("/transactions/" + ftransactionId));
							
							// file as it sits during transaction
							LocalStoreFile txfile = LocalStoreFile.of(dstore, result);
							
							scache.put(channel, RecordStruct.record()
									.with("Target", result)		// useful to support HTTP 1.1 binary uploads - this serves as Descriptor
									.with("TransactionFile", txfile)
									.with("TransactionMode", fdepmode.name())
									.with("Stream", StreamFragment.of(txfile.allocStreamDest()))
									.with("TransactionId", ftransactionId)
									.with("Extra", extra)
							);
							
							RecordStruct resp = RecordStruct.record()
									.with("Channel", channel)
									.with("TransactionId", ftransactionId)
									.with("BestEvidence", Vault.this.bestEvidence)
									.with("MinimumEvidence", Vault.this.minEvidence)
									.with("Extra", extra);
							
							Vault.this.afterStartUpload(fi, request, resp, fcb);
						}
					});
				}
			});
		} 
		catch (OperatingContextException x) {
			Logger.error("Operating context error: " + x);
			fcb.returnEmpty();
		}
	}

	public void finishUpload(RecordStruct request, boolean checkAuth, OperationOutcomeStruct fcb) throws OperatingContextException {
		try {
			String channel = request.getFieldAsString("Channel");
			
			HashMap<String, Struct> scache = OperationContext.getOrThrow().getSession().getCache();
			
			// put the FileStoreFile in cache
			Struct centry = scache.get(channel);
			
			if ((centry == null) || ! (centry instanceof RecordStruct)) {
				Logger.error("Invalid channel number, unable to finish upload.");
				fcb.returnEmpty();
				return;
			}
			
			Struct so = ((RecordStruct)centry).getFieldAsStruct("TransactionFile");
			
			if ((so == null) || ! (so instanceof FileStoreFile)) {
				Logger.error("Invalid channel number, not a stream, unable to finish upload.");
				fcb.returnEmpty();
				return;
			}
			
			FileStoreFile fi = (FileStoreFile) so;
			
			Struct tso = ((RecordStruct)centry).getFieldAsStruct("Target");
			
			if ((tso == null) || ! (tso instanceof FileStoreFile)) {
				Logger.error("Invalid channel number, no target in stream, unable to finish upload.");
				fcb.returnEmpty();
				return;
			}
			
			FileStoreFile tfi = (FileStoreFile) tso;
			
			RecordStruct extra = new RecordStruct();
			fcb.setResult(extra);
			
			String txid = ((RecordStruct)centry).getFieldAsString("TransactionId");
			TxMode txMode = TxMode.valueOf(((RecordStruct)centry).getFieldAsString("TransactionMode"));
			
			
			if ("Failure".equals(request.getFieldAsString("Status"))) {
				// TODO consider programming point
				Logger.warn("File upload incomplete or corrupt: " + tfi.getPath());
				
				if (! request.isFieldEmpty("Note"))
					Logger.warn("File upload note: " + request.getFieldAsString("Note"));

				if (txMode == TxMode.Automatic) {
					Transaction tx = Transaction.of(txid, this);
					
					tx.rollbackTransaction(new OperationOutcomeEmpty() {
						@Override
						public void callback() throws OperatingContextException {
							fcb.returnResult();
						}
					});
				}
				else {
					fcb.returnResult();
				}
				
				return;
			}
			
			RecordStruct evidinfo = request.getFieldAsRecord("Evidence");
			
			String evidenceType = null;
	
			// pick best evidence if available, we really don't care if higher is available
			if (! evidinfo.isFieldEmpty(Vault.this.bestEvidence)) {
				evidenceType = Vault.this.bestEvidence;
			}
			// else pick the highest available evidence given
			else {
				for (FieldStruct fld : evidinfo.getFields())
					evidenceType = VaultUtil.maxEvidence(fld.getName(), evidenceType);
			}
			
			String selEvidenceType = evidenceType;
			
			Consumer<Boolean> afterVerify = (pass) -> {
				try {
					OperationOutcomeEmpty finishUpload = new OperationOutcomeEmpty() {
						@Override
						public void callback() throws OperatingContextException {
							if (! request.isFieldEmpty("Note"))
								Logger.info("File upload note: " + request.getFieldAsString("Note"));
							
							Vault.this.finishUpload(tfi, request, extra, pass, selEvidenceType, fcb);
						}
					};
					
					if (pass) {
						if (VaultUtil.isSufficentEvidence(Vault.this.bestEvidence, selEvidenceType))
							Logger.info("Verified best evidence for upload: " + tfi.getPath());
						else if (VaultUtil.isSufficentEvidence(Vault.this.minEvidence, selEvidenceType))
							Logger.info("Verified minimum evidence for upload: " + tfi.getPath());
						else
							Logger.error("Verified evidence for upload, however evidence is insuffcient: " + tfi.getPath());
						
						if (txMode == TxMode.Automatic) {
							Transaction tx = Transaction.of(txid, this);
							tx.commitTransaction(null, finishUpload);
						}
						else {
							finishUpload.returnEmpty();
						}
					}
					else {
						Logger.error("File upload incomplete or corrupt: " + tfi.getPath());
						
						if (txMode == TxMode.Automatic) {
							Transaction tx = Transaction.of(txid, this);
							tx.rollbackTransaction(finishUpload);
						}
						else {
							finishUpload.returnEmpty();
						}
					}
				}
				catch (Exception x) {
					Logger.error("Operating context error: " + x);
					fcb.returnResult();
				}
			};
			
			if ("Size".equals(selEvidenceType)) {
				Long src = evidinfo.getFieldAsInteger("Size");
				long dest = fi.getSize();
				boolean match = (src == dest);
				
				if (match)
					Logger.info("File sizes match");
				else
					Logger.error("File sizes do not match");
				
				afterVerify.accept(match);
			}
			else if (StringUtil.isNotEmpty(selEvidenceType)) {
				fi.hash(selEvidenceType, new OperationOutcome<String>() {
					@Override
					public void callback(String result) throws OperatingContextException {
						if (fcb.hasErrors()) {
							afterVerify.accept(false);
						}
						else {
							String src = evidinfo.getFieldAsString(selEvidenceType);
							String dest = this.getResult();
							boolean match = (src.equals(dest));
							
							if (match)
								Logger.info("File hashes match (" + selEvidenceType + ")");
							else
								Logger.error("File hashes do not match (" + selEvidenceType + ")");
							
							afterVerify.accept(match);
						}
					}
				});
			}
			else {
				Logger.error("Missing any form of evidence, supply at least size");
				
				afterVerify.accept(false);
			}
			
		} 
		catch (OperatingContextException x) {
			Logger.error("Operating context error: " + x);
			fcb.returnResult();
		}
	}
	
	public void startDownload(RecordStruct request, boolean checkAuth, OperationOutcomeStruct fcb) throws OperatingContextException {
		try {
			// check bucket security
			if (checkAuth && ! this.checkReadAccess()) {
				Logger.errorTr(434);
				fcb.returnEmpty();
				return;
			}
			
			this.mapRequest(request, new OperationOutcome<FileStoreFile>() {
				@Override
				public void callback(FileStoreFile result) throws OperatingContextException {
					if (this.hasErrors()) {
						fcb.returnEmpty();
						return;
					}
					
					if (this.isEmptyResult()) {
						Logger.error("Your request appears valid but does not map to a file.  Unable to complete.");
						fcb.returnEmpty();
						return;
					}
					
					RecordStruct extra = RecordStruct.record();
					
					String transactionId = request.getFieldAsString("TransactionId");
					
					if (StringUtil.isEmpty(transactionId))
						transactionId = Vault.this.createTransactionId(request, extra);  // token is protected by session - session id is secure random
					
					String ftransactionId = transactionId;
					
					FileStoreFile fi = this.getResult();
					
					Vault.this.beforeStartDownload(fi, request, extra, new OperationOutcome<FileStoreFile>() {
						@Override
						public void callback(FileStoreFile result) throws OperatingContextException {
							if (this.hasErrors()) {
								fcb.returnEmpty();
								return;
							}
							
							HashMap<String, Struct> scache = OperationContext.getOrThrow().getSession().getCache();
							
							String channel = RndUtil.nextUUId();  // token is protected by session - session id is secure random
							
							/* TODO what to do with this info?
							<Field Name="Offset" Type="Integer" />
							<Field Name="TransactionId" Type="dcTinyString" />
							*/
							
							scache.put(channel, RecordStruct.record()
									.with("Target", result)		// useful to support HTTP 1.1 binary uploads - this serves as Descriptor
									.with("Stream", StreamFragment.of(result.allocStreamSrc()))
									.with("TransactionId", ftransactionId)
									.with("Extra", extra)
							);
							
							RecordStruct resp = RecordStruct.record()
									.with("Channel", channel)
									.with("TransactionId", ftransactionId)
									.with("Size", result.getSize())
									.with("BestEvidence", Vault.this.bestEvidence)
									.with("MinimumEvidence", Vault.this.minEvidence)
									.with("Extra", extra);
							
							Vault.this.afterStartDownload(fi, request, resp, fcb);
						}
					});
				}
			});
		} 
		catch (OperatingContextException x) {
			Logger.error("Operating context error: " + x);
			fcb.returnEmpty();
		}
	}
	
	public void finishDownload(RecordStruct request, boolean checkAuth, OperationOutcomeStruct fcb) throws OperatingContextException {
		try {
			String channel = request.getFieldAsString("Channel");
			
			HashMap<String, Struct> scache = OperationContext.getOrThrow().getSession().getCache();
			
			// put the FileStoreFile in cache
			Struct centry = scache.get(channel);
			
			if ((centry == null) || ! (centry instanceof RecordStruct)) {
				Logger.error("Invalid channel number, unable to finish upload.");
				fcb.returnEmpty();
				return;
			}
			
			Struct so = ((RecordStruct)centry).getFieldAsStruct("Target");
			
			if ((so == null) || ! (so instanceof FileStoreFile)) {
				Logger.error("Invalid channel number, not a stream, unable to finish upload.");
				fcb.returnEmpty();
				return;
			}
			
			FileStoreFile fi = (FileStoreFile) so;
			
			RecordStruct extra = new RecordStruct();
			
			if ("Failure".equals(request.getFieldAsString("Status"))) {
				Logger.warn("File download incomplete or corrupt: " + fi.getPath());
				
				if (!request.isFieldEmpty("Note"))
					Logger.warn("File download note: " + request.getFieldAsString("Note"));
				
				fcb.returnValue(extra);
				return;
			}
			
			RecordStruct evidinfo = request.getFieldAsRecord("Evidence");
			
			String evidenceType = null;

			// pick best evidence if available, we really don't care if higher is available
			if (! evidinfo.isFieldEmpty(Vault.this.bestEvidence)) {
				evidenceType = Vault.this.bestEvidence;
			}
			// else pick the highest available evidence given
			else {
				for (FieldStruct fld : evidinfo.getFields())
					evidenceType = VaultUtil.maxEvidence(fld.getName(), evidenceType);
			}

			String selEvidenceType = evidenceType;
			
			Consumer<Boolean> afterVerify = (pass) -> {
				if (pass) {
					if (VaultUtil.isSufficentEvidence(Vault.this.bestEvidence, selEvidenceType))
						Logger.info("Verified best evidence for download: " + fi.getPath());
					else if (VaultUtil.isSufficentEvidence(Vault.this.minEvidence, selEvidenceType))
						Logger.info("Verified minimum evidence for download: " + fi.getPath());
					else
						Logger.error("Verified evidence for download, however evidence is insuffcient: " + fi.getPath());
				}
				else {
					Logger.error("File download incomplete or corrupt: " + fi.getPath());
				}
				
				if (! request.isFieldEmpty("Note"))
					Logger.info("File download note: " + request.getFieldAsString("Note"));
				
				//fcb.complete(extra);	-- will extra still get in if passed this way?
				Vault.this.finishDownload(fi, request, extra, pass, selEvidenceType, fcb);
			};
			
			if ("Size".equals(selEvidenceType)) {
				Long src = evidinfo.getFieldAsInteger("Size");
				long dest = fi.getSize();
				boolean match = (src == dest);
				
				if (match)
					Logger.info("File sizes match");
				else
					Logger.error("File sizes do not match");
				
				afterVerify.accept(match);
			}
			else if (StringUtil.isNotEmpty(selEvidenceType)) {
				fi.hash(selEvidenceType, new OperationOutcome<String>() {
					@Override
					public void callback(String result) throws OperatingContextException {
						if (fcb.hasErrors()) {
							afterVerify.accept(false);
						}
						else {
							String src = evidinfo.getFieldAsString(selEvidenceType);
							String dest = this.getResult();
							boolean match = (src.equals(dest));
							
							if (match)
								Logger.info("File hashes match (" + selEvidenceType + ")");
							else
								Logger.error("File hashes do not match (" + selEvidenceType + ")");
							
							afterVerify.accept(match);
						}
					}
				});
			}
			else {
				Logger.error("Missing any form of evidence, supply at least size");
				
				afterVerify.accept(false);
			}
		}
		catch (OperatingContextException x) {
			Logger.error("Operating context error: " + x);
			fcb.returnEmpty();
		}
	}
	
		/* TODO review - not a terrible idea
	protected void watch(String op, FileStoreFile file) {
		XElement settings = this.getLoader().getSettings();
		
		if (settings != null) {
	        for (XElement watch : settings.selectAll("Watch")) {
	        	String wpath = watch.getAttribute("FilePath");
	        	
	        	// if we are filtering on path make sure the path is a parent of the triggered path
	        	if (StringUtil.isNotEmpty(wpath)) {
	        		CommonPath wp = new CommonPath(wpath);
	        		
	        		if (!wp.isParent(file.path()))
	        			continue;
	        	}
        	
                String tasktag = op + "Task";
                
    			for (XElement task : watch.selectAll(tasktag)) {
    				String id = task.getAttribute("Id");
    				
    				if (StringUtil.isEmpty(id))
    					id = Task.nextTaskId();
    				
    				String title = task.getAttribute("Title");			        				
    				String scriptold = task.getAttribute("Script");
    				String params = task.selectFirstText("Params");
    				RecordStruct prec = null;
    				
    				if (StringUtil.isNotEmpty(params)) {
    					FuncResult<CompositeStruct> pres = CompositeParser.parseJson(params);
    					
    					if (pres.isNotEmptyResult())
    						prec = (RecordStruct) pres.getResult();
    				}
    				
    				if (prec == null) 
    					prec = new RecordStruct();
    				
			        prec.setField("File", file);
    				
    				if (scriptold.startsWith("$"))
    					scriptold = scriptold.substring(1);
    				
    				Task t = new Task()
    					.withId(id)
    					.withTitle(title)
    					.withParams(prec)
    					.withRootContext();
    				
    				if (!ScriptWork.addScript(t, Paths.get(scriptold))) {
    					Logger.error("Unable to run scriptold for file watcher: " + watch.getAttribute("FilePath"));
    					continue;
    				}
    				
    				Hub.instance.getWorkPool().submit(t);
    			}
	        }
		}
	}
		*/
}
