package dcraft.filevault;

import dcraft.filestore.CommonPath;
import dcraft.filestore.FileStoreFile;
import dcraft.hub.op.*;
import dcraft.log.Logger;
import dcraft.service.ServiceHub;
import dcraft.service.ServiceRequest;
import dcraft.session.Session;
import dcraft.session.SessionHub;
import dcraft.stream.StreamFragment;
import dcraft.stream.StreamWork;
import dcraft.struct.RecordStruct;
import dcraft.struct.Struct;
import dcraft.struct.scalar.BooleanStruct;
import dcraft.struct.scalar.StringStruct;
import dcraft.task.Task;
import dcraft.task.TaskContext;
import dcraft.task.TaskHub;
import dcraft.task.TaskObserver;
import dcraft.util.StringUtil;

import java.util.HashMap;

public class VaultUtil {
	static public void transfer(String vname, FileStoreFile upfile, CommonPath destpath, String token, OperationOutcomeStruct callback) throws OperatingContextException {
		Session sess = OperationContext.getOrThrow().getSession();

		// transfers need a session, assign one
		if (sess == null) {
			sess = Session.of("transfer:", OperationContext.getOrThrow().getUserContext());

			SessionHub.register(sess);

			OperationContext.getOrThrow().setSessionId(sess.getId());
		}

		if (StringUtil.isNotEmpty(token))
			VaultUtil.setSessionToken(token);

		VaultUtil.transferAfterToken(vname, upfile, destpath, token, callback);
	}

	static public void prepTxTransfer(String token) throws OperatingContextException {
		String txid = Transaction.createTransactionId();

		VaultUtil.prepTxTransfer(token, txid);
	}

	static public void prepTxTransfer(String token, String txid) throws OperatingContextException {
		Session sess = OperationContext.getOrThrow().getSession();

		// transfers need a session, assign one
		if (sess == null) {
			sess = Session.of("transfer:", OperationContext.getOrThrow().getUserContext());

			SessionHub.register(sess);

			OperationContext.getOrThrow().setSessionId(sess.getId());
		}

		VaultUtil.setSessionToken(token, txid);
	}

	static public void transferAfterToken(String vname, FileStoreFile upfile, CommonPath destpath, String token, OperationOutcomeStruct callback) throws OperatingContextException {
		ServiceHub.call(VaultServiceRequest.ofStartUpload(vname)
				.withPath(destpath)
				.withSize(upfile.getSize())
				.withToken(token)
				.withOverwrite(true)
				.withOutcome(new OperationOutcomeStruct() {
					@Override
					public void callback(Struct result) throws OperatingContextException {
						if (this.hasErrors()) {
							Logger.error("Transfer start failed");
							return;
						}

						RecordStruct rec = (RecordStruct) result;

						String channel = rec.getFieldAsString("Channel");

						VaultUtil.transfer(channel, upfile.allocStreamSrc(),null,
								new OperationOutcomeEmpty() {
									@Override
									public void callback() throws OperatingContextException {
										if (this.hasErrors()) {
											Logger.error("Transfer upload failed");
											return;
										}

										ServiceHub.call(VaultServiceRequest.ofFinishUpload(vname)
												.withPath(destpath)
												.withChannel(channel)
												.withStatus("Success")
												.withEvidence("Size", upfile.getSize())
												.withOutcome(callback)
										);
									}
								});
					}
				})
		);
	}

	static public void transfer(String channel, StreamFragment source, StreamFragment dest, OperationOutcomeEmpty callback) {
		OperationContext currctx = OperationContext.getOrNull();

		try {
			HashMap<String, Struct> scache = currctx.getSession().getCache();

			// put the FileStoreFile in cache
			Struct centry = scache.get(channel);

			if ((centry == null) || ! (centry instanceof RecordStruct)) {
				Logger.error("Invalid channel number, unable to transfer.");
				callback.returnEmpty();
				return;
			}

			Object so = ((RecordStruct)centry).getFieldAsRecord("Stream");

			if ((so == null) || ! (so instanceof StreamFragment)) {
				Logger.error("Invalid channel number, not a stream, unable to transfer.");
				callback.returnEmpty();
				return;
			}

			Task task = Task
					.ofSubtask("API Transfer Stream", "XFR")
					.withWork(StreamWork.of(source, (StreamFragment) so, dest));

			TaskHub.submit(task, new TaskObserver() {
				@Override
				public void callback(TaskContext subtask) {
					callback.returnEmpty();
				}
			});
		}
		catch (OperatingContextException x) {
			Logger.warn("Unexpected error: " + x);
			callback.returnEmpty();
		}
		finally {
			OperationContext.set(currctx);
		}
	}

	static public boolean isSufficentEvidence(String lookingfor, String got) {
		if ("Size".equals(lookingfor)) 
			return ("Size".equals(got)  || "MD5".equals(got) || "SHA128".equals(got) || "SHA256".equals(got) || "SHA512".equals(got));
		
		if ("MD5".equals(lookingfor)) 
			return ("MD5".equals(got) || "SHA128".equals(got) || "SHA256".equals(got) || "SHA512".equals(got));
		
		if ("SHA128".equals(lookingfor)) 
			return ("SHA128".equals(got) || "SHA256".equals(got) || "SHA512".equals(got));
		
		if ("SHA256".equals(lookingfor)) 
			return ("SHA256".equals(got) || "SHA512".equals(got));
		
		if ("SHA512".equals(lookingfor)) 
			return ("SHA512".equals(got));
		
		return false;
	}
	
	static public String maxEvidence(String lhs, String rhs) {
		if ("Size".equals(lhs) && ("MD5".equals(rhs) || "SHA128".equals(rhs) || "SHA256".equals(rhs) || "SHA512".equals(rhs)))
			return rhs;
		
		if ("MD5".equals(lhs) && ("SHA128".equals(rhs) || "SHA256".equals(rhs) || "SHA512".equals(rhs)))
			return rhs;
		
		if ("SHA128".equals(lhs) && ("SHA256".equals(rhs) || "SHA512".equals(rhs)))
			return rhs;
		
		if ("SHA256".equals(lhs) && "SHA512".equals(rhs))
			return rhs;
		
		return lhs;
	}
	
	static public void setSessionToken(String token) throws OperatingContextException {
		Session session = OperationContext.getOrThrow().getSession();

		if (session != null) {
			HashMap<String, Struct> scache = session.getCache();

			scache.put(token, BooleanStruct.of(true));
		}
	}

	static public void setSessionToken(String token, String txid) throws OperatingContextException {
		Session session = OperationContext.getOrThrow().getSession();

		if (session != null) {
			HashMap<String, Struct> scache = session.getCache();

			scache.put(token, BooleanStruct.of(true));
			scache.put(token + "Tx", StringStruct.of(txid));
		}
	}

	static public void clearSessionToken(String token) throws OperatingContextException {
		Session session = OperationContext.getOrThrow().getSession();

		if (session != null) {
			HashMap<String, Struct> scache = session.getCache();

			scache.remove(token);
			scache.remove(token + "Tx");
		}
	}

	static public String getSessionTokenTx(String token) throws OperatingContextException {
		Session session = OperationContext.getOrThrow().getSession();

		if (session == null)
			return null;

		HashMap<String, Struct> scache = session.getCache();

		if (! scache.containsKey(token))
			return null;

		return Struct.objectToString(scache.get(token + "Tx"));
	}
}
