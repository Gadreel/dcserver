/* ************************************************************************
#
#  designCraft.io
#
#  http://designcraft.io/
#
#  Copyright:
#    Copyright 2014 eTimeline, LLC. All rights reserved.
#
#  License:
#    See the license.txt file in the project's top-level directory for details.
#
#  Authors:
#    * Andy White
#
************************************************************************ */
package dcraft.service.base;

import dcraft.filestore.FileDescriptor;
import dcraft.filestore.local.LocalStore;
import dcraft.filestore.local.LocalStoreFile;
import dcraft.filevault.DepositHub;
import dcraft.filevault.Transaction;
import dcraft.filevault.TxMode;
import dcraft.filevault.Vault;
import dcraft.filevault.VaultUtil;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
import dcraft.hub.op.OperationOutcome;
import dcraft.hub.op.OperationOutcomeEmpty;
import dcraft.hub.op.OperationOutcomeString;
import dcraft.hub.op.OperationOutcomeStruct;
import dcraft.log.Logger;
import dcraft.service.ServiceRequest;
import dcraft.stream.StreamFragment;
import dcraft.struct.FieldStruct;
import dcraft.struct.ListStruct;
import dcraft.struct.RecordStruct;
import dcraft.struct.Struct;
import dcraft.tenant.Site;
import dcraft.util.RndUtil;
import dcraft.util.StringUtil;

import java.util.HashMap;
import java.util.List;
import java.util.function.Consumer;

public class Vaults  {
	static public boolean handle(ServiceRequest request, OperationOutcomeStruct callback) throws OperatingContextException {
		RecordStruct rec = request.getDataAsRecord();
		RecordStruct params = rec.getFieldAsRecord("Params");
		
		Site site = OperationContext.getOrThrow().getUserContext().getSite();
		
		Vault vault = site.getVault(rec.getFieldAsString("Vault"));

		if (vault == null) {
			Logger.error("Missing vault.");
			return true;
		}
		
		String op = request.getOp();
		
		if ("AllocateUploadToken".equals(op)) {
			allocateUploadToken(vault, rec, request.isFromRpc(), callback);
			return true;
		}
		
		if ("BeginTransaction".equals(op)) {
			beginTransaction(vault, rec, callback);
			return true;
		}
		
		if ("CommitTransaction".equals(op)) {
			commitTransaction(vault, rec, callback);
			return true;
		}
		
		if ("RollbackTransaction".equals(op)) {
			rollbackTransaction(vault, rec, callback);
			return true;
		}
		
		if ("FileDetail".equals(op)) {
			getFileDetail(vault, rec, request.isFromRpc(), new OperationOutcome<FileDescriptor>() {
				@Override
				public void callback(FileDescriptor result) throws OperatingContextException {
					if (! this.hasErrors()) {
						RecordStruct filerec = toExternal(result);
						
						String meth = rec.getFieldAsString("Method");
						
						if (StringUtil.isEmpty(meth) || result.isFolder()) {
							callback.returnValue(filerec);
							return;
						}
						
						vault.hashFile(result, meth, params, new OperationOutcomeString() {
							@Override
							public void callback(String result) throws OperatingContextException {
								if (! this.hasErrors()) {
									filerec.with("Hash", result);
									callback.returnValue(filerec);
								}
								else {
									callback.returnEmpty();
								}
							}
						});
					}
					else {
						callback.returnEmpty();
					}
				}
			});
			
			return true;
		}
		
		if ("Delete".equals(op) || "DeleteFile".equals(op) || "DeleteFolder".equals(op)) {
			deleteFile(vault, rec, request.isFromRpc(), callback);
			return true;
		}
		
		if ("AddFolder".equals(op)) {
			addFolder(vault, rec, request.isFromRpc(), callback);
			return true;
		}

		if ("Rename".equals(op)) {
			rename(vault, rec, request.isFromRpc(), callback);
			return true;
		}

		if ("Move".equals(op)) {
			move(vault, rec, request.isFromRpc(), callback);
			return true;
		}

		if ("ListFiles".equals(op)) {
			listFiles(vault, rec, request.isFromRpc(), new OperationOutcome<List<? extends FileDescriptor>>() {
				@Override
				public void callback(List<? extends FileDescriptor> result) throws OperatingContextException {
					if (! this.hasErrors()) {
						ListStruct files = new ListStruct();
						
						for (FileDescriptor file : result) {
							files.withItem(toExternal(file));
						}
						
						callback.returnValue(files);
					}
					else {
						callback.returnEmpty();
					}
				}
			});
			return true;
		}
		
		if ("Custom".equals(op)) {
			executeCustom(vault, rec, request.isFromRpc(), callback);
			return true;
		}
		
		if ("StartUpload".equals(op)) {
			startUpload(vault, rec, request.isFromRpc(), callback);
			return true;
		}
		
		if ("FinishUpload".equals(op)) {
			finishUpload(vault, rec, request.isFromRpc(), callback);
			return true;
		}
		
		if ("StartDownload".equals(op)) {
			startDownload(vault, rec, request.isFromRpc(), callback);
			return true;
		}
		
		if ("FinishDownload".equals(op)) {
			finishDownload(vault, rec, request.isFromRpc(), callback);
			return true;
		}
		
		return false;
	}
	
	static protected RecordStruct toExternal(FileDescriptor file) throws OperatingContextException {
		return RecordStruct.record()
				.with("FileName", file.getName())
				.with("IsFolder", file.isFolder())
				.with("Modified", file.getModificationAsTime())
				.with("Size", file.getSize())
				.with("Extra", file.getExtra());
	}
	
	static public void executeCustom(Vault vault, RecordStruct request, boolean checkAuth, OperationOutcomeStruct fcb) throws OperatingContextException {
		try {
			// check bucket security
			if (checkAuth && ! vault.checkCustomAccess(request.getFieldAsString("Command"), request.getFieldAsString("Path"), request.getFieldAsRecord("Params"))) {
				Logger.errorTr(434);
				fcb.returnEmpty();
				return;
			}
			
			vault.executeCustom(request, fcb);
		}
		catch (OperatingContextException x) {
			Logger.error("Operating context error: " + x);
			fcb.returnEmpty();
		}
	}
	
	static public void getFileDetail(Vault vault, RecordStruct request, boolean checkAuth, OperationOutcome<FileDescriptor> fcb) throws OperatingContextException {
		RecordStruct params = request.getFieldAsRecord("Params");
		
		try {
			// check bucket security
			if (checkAuth && ! vault.checkReadAccess("FileDetail", request.getFieldAsString("Params"), params)) {
				Logger.errorTr(434);
				fcb.returnEmpty();
				return;
			}
			
			vault.getMappedFileDetail(request.getFieldAsString("Path"), params, new OperationOutcome<FileDescriptor>() {
				@Override
				public void callback(FileDescriptor result) throws OperatingContextException {
					if (this.hasErrors()) {
						fcb.returnEmpty();
						return;
					}
					
					if (this.isEmptyResult()) {
						Logger.error("Your request appears valid but does not map to a file.  Unable to complete.");
						fcb.returnEmpty();
						return;
					}
					
					FileDescriptor fi = this.getResult();
					
					if (! fi.exists()) {
						Logger.error("File does not exist");
						fcb.returnEmpty();
						return;
					}
					
					fcb.returnValue(fi);
				}
			});
		}
		catch (OperatingContextException x) {
			Logger.error("Operating context error: " + x);
			fcb.returnEmpty();
		}
	}
	
	static public void deleteFile(Vault vault, RecordStruct request, boolean checkAuth, OperationOutcomeStruct fcb) throws OperatingContextException {
		RecordStruct params = request.getFieldAsRecord("Params");
		
		try {
			// check bucket security
			if (checkAuth && ! vault.checkWriteAccess("DeleteFile", request.getFieldAsString("Path"), params)) {
				Logger.errorTr(434);
				fcb.returnValue(null);
				return;
			}
			
			vault.getMappedFileDetail(request.getFieldAsString("Path"), params, new OperationOutcome<FileDescriptor>() {
				@Override
				public void callback(FileDescriptor result) throws OperatingContextException {
					if (this.hasErrors()) {
						fcb.returnEmpty();
						return;
					}
					
					if (this.isEmptyResult()) {
						Logger.error("Your request appears valid but does not map to a file.  Unable to complete.");
						fcb.returnEmpty();
						return;
					}
					
					FileDescriptor fi = this.getResult();
					
					vault.deleteFile(fi, params, new OperationOutcomeEmpty() {
						@Override
						public void callback() throws OperatingContextException {
							fcb.returnEmpty();
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

	static public void rename(Vault vault, RecordStruct request, boolean checkAuth, OperationOutcomeStruct fcb) throws OperatingContextException {
		RecordStruct params = request.getFieldAsRecord("Params");

		try {
			// check bucket security
			if (checkAuth && ! vault.checkWriteAccess("DeleteFile", request.getFieldAsString("Path"), params)) {
				Logger.errorTr(434);
				fcb.returnValue(null);
				return;
			}

			vault.getMappedFileDetail(request.getFieldAsString("Path"), params, new OperationOutcome<FileDescriptor>() {
				@Override
				public void callback(FileDescriptor result) throws OperatingContextException {
					if (this.hasErrors()) {
						fcb.returnEmpty();
						return;
					}

					if (this.isEmptyResult()) {
						Logger.error("Your request appears valid but does not map to a file.  Unable to complete.");
						fcb.returnEmpty();
						return;
					}

					vault.renameFiles(result, params.getFieldAsList("Files"), params, new OperationOutcomeEmpty() {
						@Override
						public void callback() throws OperatingContextException {
							fcb.returnEmpty();
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

	static public void move(Vault vault, RecordStruct request, boolean checkAuth, OperationOutcomeStruct fcb) throws OperatingContextException {
		RecordStruct params = request.getFieldAsRecord("Params");

		try {
			// check bucket security - TODO check dest access too?
			if (checkAuth && ! vault.checkWriteAccess("DeleteFile", request.getFieldAsString("Path"), params)) {
				Logger.errorTr(434);
				fcb.returnValue(null);
				return;
			}

			vault.getMappedFileDetail(request.getFieldAsString("Path"), params, new OperationOutcome<FileDescriptor>() {
				@Override
				public void callback(FileDescriptor result) throws OperatingContextException {
					if (this.hasErrors()) {
						fcb.returnEmpty();
						return;
					}

					if (this.isEmptyResult()) {
						Logger.error("Your request appears valid but does not map to a file.  Unable to complete.");
						fcb.returnEmpty();
						return;
					}

					vault.getMappedFileDetail(request.getFieldAsString("DestinationPath"), params, new OperationOutcome<FileDescriptor>() {
						@Override
						public void callback(FileDescriptor dresult) throws OperatingContextException {
							if (this.hasErrors()) {
								fcb.returnEmpty();
								return;
							}

							if (this.isEmptyResult()) {
								Logger.error("Your request appears valid but does not map to a destination file.  Unable to complete.");
								fcb.returnEmpty();
								return;
							}

							vault.moveFiles(result, dresult, params, new OperationOutcomeEmpty() {
								@Override
								public void callback() throws OperatingContextException {
									fcb.returnEmpty();
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

	static public void addFolder(Vault vault, RecordStruct request, boolean checkAuth, OperationOutcomeStruct fcb) throws OperatingContextException {
		RecordStruct params = request.getFieldAsRecord("Params");
		
		try {
			// check bucket security
			if (checkAuth && ! vault.checkWriteAccess("AddFolder", request.getFieldAsString("Path"), params)) {
				Logger.errorTr(434);
				fcb.returnEmpty();
				return;
			}
			
			vault.getMappedFileDetail(request.getFieldAsString("Path"), params, new OperationOutcome<FileDescriptor>() {
				@Override
				public void callback(FileDescriptor result) throws OperatingContextException {
					if (this.hasErrors()) {
						fcb.returnEmpty();
						return;
					}
					
					if (this.isEmptyResult()) {
						Logger.error("Your request appears valid but does not map to a file.  Unable to complete.");
						fcb.returnEmpty();
						return;
					}
					
					FileDescriptor fi = this.getResult();
					
					if (fi.exists() && fi.isFolder()) {
						fcb.returnEmpty();
						return;
					}
					
					if (fi.exists() && !fi.isFolder()) {
						Logger.error("Path already maps to a file, unable to create folder");
						fcb.returnEmpty();
						return;
					}
					
					vault.addFolder(fi.getPathAsCommon(), params, new OperationOutcome<FileDescriptor>() {
						@Override
						public void callback(FileDescriptor result) throws OperatingContextException {
							fcb.returnEmpty();
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
	static public void listFiles(Vault vault, RecordStruct request, boolean checkAuth, OperationOutcome<List<? extends FileDescriptor>> fcb) throws OperatingContextException {
		RecordStruct params = request.getFieldAsRecord("Params");
		
		try {
			// check bucket security
			if (checkAuth && ! vault.checkReadAccess("ListFiles", request.getFieldAsString("Path"), params)) {
				Logger.errorTr(434);
				fcb.returnEmpty();
				return;
			}
			
			vault.getMappedFileDetail(request.getFieldAsString("Path"), params, new OperationOutcome<FileDescriptor>() {
				@Override
				public void callback(FileDescriptor result) throws OperatingContextException {
					if (this.hasErrors()) {
						fcb.returnEmpty();
						return;
					}
					
					if (this.isEmptyResult()) {
						Logger.error("Your request appears valid but does not map to a file.  Unable to complete.");
						fcb.returnEmpty();
						return;
					}
					
					FileDescriptor fi = this.getResult();
					
					if (!fi.exists()) {
						fcb.returnEmpty();
						return;
					}
					
					vault.getFolderListing(fi, params, new OperationOutcome<List<? extends FileDescriptor>>() {
						@Override
						public void callback(List<? extends FileDescriptor> result) throws OperatingContextException {
							if (this.hasErrors()) {
								fcb.returnEmpty();
							}
							else {
								fcb.returnValue(result);
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
	
	static public void allocateUploadToken(Vault vault, RecordStruct request, boolean checkAuth, OperationOutcomeStruct fcb) throws OperatingContextException {
		RecordStruct params = request.getFieldAsRecord("Params");
		
		if (vault.tryExecuteMethod("AllocateUploadToken", request, fcb))
			return;
		
		try {
			String token = null;
			
			if ((params != null) && params.isNotFieldEmpty("Token"))
				token = params.getFieldAsString("Token");
			else
				token = RndUtil.nextUUId();  // token is protected by session - session id is secure random
			
			VaultUtil.setSessionToken(token);
			
			fcb.returnValue(RecordStruct.record()
					.with("Token", token)
			);
		}
		catch (OperatingContextException x) {
			Logger.error("Operating context error: " + x);
			fcb.returnEmpty();
		}
	}
	
	static public void beginTransaction(Vault vault, RecordStruct request, OperationOutcomeStruct fcb) throws OperatingContextException {
		fcb.returnValue(RecordStruct.record()
				.with("TransactionId", Transaction.createTransactionId())
				.with("Extra", null)
		);
	}
	
	static public void commitTransaction(Vault vault, RecordStruct request, OperationOutcomeStruct fcb) throws OperatingContextException {
		RecordStruct params = request.getFieldAsRecord("Params");
		
		// TODO verify requested id is allowed in this session
		String transactionId = request.getFieldAsString("TransactionId");

		if (StringUtil.isEmpty(transactionId)) {
			transactionId = vault.getTxForToken(request);
		}

		if (StringUtil.isNotEmpty(transactionId)) {
			Transaction tx = vault.buildUpdateTransaction(transactionId, params);

			tx.commitTransaction(new OperationOutcomeEmpty() {
				@Override
				public void callback() throws OperatingContextException {
					vault.clearToken(request);

					fcb.returnEmpty();
				}
			});
		}
		else {
			Logger.error("Missing Tx Id");
			fcb.returnEmpty();
		}
	}
	
	static public void rollbackTransaction(Vault vault, RecordStruct request, OperationOutcomeStruct fcb) throws OperatingContextException {
		RecordStruct params = request.getFieldAsRecord("Params");

		// TODO verify requested id is allowed in this session
		String transactionId = request.getFieldAsString("TransactionId");

		if (StringUtil.isEmpty(transactionId)) {
			transactionId = vault.getTxForToken(request);
		}

		if (StringUtil.isNotEmpty(transactionId)) {
			Transaction tx = vault.buildUpdateTransaction(transactionId, params);

			tx.rollbackTransaction(new OperationOutcomeEmpty() {
				@Override
				public void callback() throws OperatingContextException {
					vault.clearToken(request);

					fcb.returnEmpty();
				}
			});
		}
		else {
			Logger.error("Missing Tx Id");
			fcb.returnEmpty();
		}
	}
	
	static public void startUpload(Vault vault, RecordStruct request, boolean checkAuth, OperationOutcomeStruct fcb) throws OperatingContextException {
		RecordStruct params = request.getFieldAsRecord("Params");
		
		try {
			// check bucket security
			if (checkAuth && ! vault.checkWriteAccess("StartUpload", request.getFieldAsString("Path"), params) && ! vault.isUploadtokenRequired()) {
				Logger.errorTr(434);
				fcb.returnEmpty();
				return;
			}
			
			vault.getMappedFileDetail(request.getFieldAsString("Path"), params, new OperationOutcome<FileDescriptor>() {
				@Override
				public void callback(FileDescriptor result) throws OperatingContextException {
					if (this.hasErrors()) {
						fcb.returnEmpty();
						return;
					}
					
					if (this.isEmptyResult()) {
						Logger.error("Your request appears valid but does not map to a file.  Unable to complete.");
						fcb.returnEmpty();
						return;
					}
					
					FileDescriptor fi = this.getResult();
					
					if (! vault.checkUploadToken(request, fi)) {
						Logger.error("You may not upload to this path.");
						fcb.returnEmpty();
						return;
					}
					
					String transactionId = request.getFieldAsString("TransactionId");
					TxMode depmode = TxMode.Manual;
					
					if (StringUtil.isEmpty(transactionId)) {
						transactionId = vault.getTxForToken(request);

						if (StringUtil.isEmpty(transactionId)) {
							transactionId = Transaction.createTransactionId();  // token is protected by session - session id is secure random
							depmode = TxMode.Automatic;
						}
					}
					
					String ftransactionId = transactionId;
					TxMode fdepmode = depmode;
					
					vault.beforeStartUpload(fi, params, new OperationOutcome<FileDescriptor>() {
						@Override
						public void callback(FileDescriptor result) throws OperatingContextException {
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
									.with("Extra", params)
							);
							
							RecordStruct resp = RecordStruct.record()
									.with("Channel", channel)
									.with("TransactionId", ftransactionId)
									.with("BestEvidence", vault.getBestEvidence())
									.with("MinimumEvidence", vault.getMinEvidence())
									.with("Extra", params);
							
							vault.afterStartUpload(fi, resp, fcb);
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
	
	static public void finishUpload(Vault vault, RecordStruct request, boolean checkAuth, OperationOutcomeStruct fcb) throws OperatingContextException {
		RecordStruct params = request.getFieldAsRecord("Params");
		
		try {
			String channel = request.getFieldAsString("Channel");
			
			HashMap<String, Struct> scache = OperationContext.getOrThrow().getSession().getCache();
			
			// put the FileDescriptor in cache
			Struct centry = scache.get(channel);
			
			if ((centry == null) || ! (centry instanceof RecordStruct)) {
				Logger.error("Invalid channel number, unable to finish upload.");
				fcb.returnEmpty();
				return;
			}
			
			Struct so = ((RecordStruct)centry).getFieldAsStruct("TransactionFile");
			
			if ((so == null) || ! (so instanceof FileDescriptor)) {
				Logger.error("Invalid channel number, not a stream, unable to finish upload.");
				fcb.returnEmpty();
				return;
			}
			
			FileDescriptor fi = (FileDescriptor) so;
			
			Struct tso = ((RecordStruct)centry).getFieldAsStruct("Target");
			
			if ((tso == null) || ! (tso instanceof FileDescriptor)) {
				Logger.error("Invalid channel number, no target in stream, unable to finish upload.");
				fcb.returnEmpty();
				return;
			}
			
			FileDescriptor tfi = (FileDescriptor) tso;
			
			String txid = ((RecordStruct)centry).getFieldAsString("TransactionId");
			TxMode txMode = TxMode.valueOf(((RecordStruct)centry).getFieldAsString("TransactionMode"));
			
			if ("Failure".equals(request.getFieldAsString("Status"))) {
				// TODO consider programming point
				Logger.warn("File upload incomplete or corrupt: " + tfi.getPath());
				
				if (! request.isFieldEmpty("Note"))
					Logger.warn("File upload note: " + request.getFieldAsString("Note"));
				
				if (txMode == TxMode.Automatic) {
					Transaction tx = vault.buildUpdateTransaction(txid, params);
					
					tx.rollbackTransaction(new OperationOutcomeEmpty() {
						@Override
						public void callback() throws OperatingContextException {
							fcb.returnEmpty();
						}
					});
				}
				else {
					fcb.returnEmpty();
				}
				
				return;
			}
			
			RecordStruct evidinfo = request.getFieldAsRecord("Evidence");
			
			String evidenceType = null;
			
			// pick best evidence if available, we really don't care if higher is available
			if (! evidinfo.isFieldEmpty(vault.getBestEvidence())) {
				evidenceType = vault.getBestEvidence();
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
							
							vault.finishUpload(tfi, params, pass, selEvidenceType, fcb);
						}
					};
					
					if (pass) {
						if (VaultUtil.isSufficentEvidence(vault.getBestEvidence(), selEvidenceType))
							Logger.info("Verified best evidence for upload: " + tfi.getPath());
						else if (VaultUtil.isSufficentEvidence(vault.getMinEvidence(), selEvidenceType))
							Logger.info("Verified minimum evidence for upload: " + tfi.getPath());
						else
							Logger.error("Verified evidence for upload, however evidence is insuffcient: " + tfi.getPath());
						
						if (txMode == TxMode.Automatic) {
							Transaction tx = vault.buildUpdateTransaction(txid, params);
							tx.commitTransaction(finishUpload);
						}
						else {
							finishUpload.returnEmpty();
						}
					}
					else {
						Logger.error("File upload incomplete or corrupt: " + tfi.getPath());
						
						if (txMode == TxMode.Automatic) {
							Transaction tx = vault.buildUpdateTransaction(txid, params);
							tx.rollbackTransaction(finishUpload);
						}
						else {
							finishUpload.returnEmpty();
						}
					}
				}
				catch (Exception x) {
					Logger.error("Operating context error: " + x);
					fcb.returnEmpty();
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
				vault.hashFile(fi, selEvidenceType, params, new OperationOutcomeString() {
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
	
	static public void startDownload(Vault vault, RecordStruct request, boolean checkAuth, OperationOutcomeStruct fcb) throws OperatingContextException {
		RecordStruct params = request.getFieldAsRecord("Params");
		
		try {
			// check bucket security
			if (checkAuth && ! vault.checkReadAccess("StartDownload", request.getFieldAsString("Path"), params)) {
				Logger.errorTr(434);
				fcb.returnEmpty();
				return;
			}
			
			vault.getMappedFileDetail(request.getFieldAsString("Path"), params, new OperationOutcome<FileDescriptor>() {
				@Override
				public void callback(FileDescriptor result) throws OperatingContextException {
					if (this.hasErrors()) {
						fcb.returnEmpty();
						return;
					}
					
					if (this.isEmptyResult()) {
						Logger.error("Your request appears valid but does not map to a file.  Unable to complete.");
						fcb.returnEmpty();
						return;
					}
					
					String transactionId = request.getFieldAsString("TransactionId");
					
					if (StringUtil.isEmpty(transactionId))
						transactionId = Transaction.createTransactionId();  // token is protected by session - session id is secure random
					
					String ftransactionId = transactionId;
					
					FileDescriptor fi = this.getResult();
					
					vault.beforeStartDownload(fi, params, new OperationOutcome<FileDescriptor>() {
						@Override
						public void callback(FileDescriptor result) throws OperatingContextException {
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
									.with("Stream", vault.toSourceStream(result))
									.with("TransactionId", ftransactionId)
									.with("Extra", params)
							);
							
							RecordStruct resp = RecordStruct.record()
									.with("Channel", channel)
									.with("TransactionId", ftransactionId)
									.with("Size", result.getSize())
									.with("BestEvidence", vault.getBestEvidence())
									.with("MinimumEvidence", vault.getMinEvidence())
									.with("Extra", params);
							
							vault.afterStartDownload(fi, resp, fcb);
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
	
	static public void finishDownload(Vault vault, RecordStruct request, boolean checkAuth, OperationOutcomeStruct fcb) throws OperatingContextException {
		RecordStruct params = request.getFieldAsRecord("Params");
		
		try {
			String channel = request.getFieldAsString("Channel");
			
			HashMap<String, Struct> scache = OperationContext.getOrThrow().getSession().getCache();
			
			// put the FileDescriptor in cache
			Struct centry = scache.get(channel);
			
			if ((centry == null) || ! (centry instanceof RecordStruct)) {
				Logger.error("Invalid channel number, unable to finish upload.");
				fcb.returnEmpty();
				return;
			}
			
			Struct so = ((RecordStruct)centry).getFieldAsStruct("Target");
			
			if ((so == null) || ! (so instanceof FileDescriptor)) {
				Logger.error("Invalid channel number, not a stream, unable to finish upload.");
				fcb.returnEmpty();
				return;
			}
			
			FileDescriptor fi = (FileDescriptor) so;
			
			if ("Failure".equals(request.getFieldAsString("Status"))) {
				Logger.warn("File download incomplete or corrupt: " + fi.getPath());
				
				if (!request.isFieldEmpty("Note"))
					Logger.warn("File download note: " + request.getFieldAsString("Note"));
				
				fcb.returnValue(params);
				return;
			}
			
			RecordStruct evidinfo = request.getFieldAsRecord("Evidence");
			
			String evidenceType = null;
			
			// pick best evidence if available, we really don't care if higher is available
			if (! evidinfo.isFieldEmpty(vault.getBestEvidence())) {
				evidenceType = vault.getBestEvidence();
			}
			// else pick the highest available evidence given
			else {
				for (FieldStruct fld : evidinfo.getFields())
					evidenceType = VaultUtil.maxEvidence(fld.getName(), evidenceType);
			}
			
			String selEvidenceType = evidenceType;
			
			Consumer<Boolean> afterVerify = (pass) -> {
				if (pass) {
					if (VaultUtil.isSufficentEvidence(vault.getBestEvidence(), selEvidenceType))
						Logger.info("Verified best evidence for download: " + fi.getPath());
					else if (VaultUtil.isSufficentEvidence(vault.getMinEvidence(), selEvidenceType))
						Logger.info("Verified minimum evidence for download: " + fi.getPath());
					else
						Logger.error("Verified evidence for download, however evidence is insuffcient: " + fi.getPath());
				}
				else {
					Logger.error("File download incomplete or corrupt: " + fi.getPath());
				}
				
				if (! request.isFieldEmpty("Note"))
					Logger.info("File download note: " + request.getFieldAsString("Note"));
				
				//fcb.complete(params);	-- will params still get in if passed this way?
				vault.finishDownload(fi, request, params, pass, selEvidenceType, fcb);
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
				vault.hashFile(fi, selEvidenceType, params, new OperationOutcomeString() {
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
}
