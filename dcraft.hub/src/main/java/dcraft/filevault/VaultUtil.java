package dcraft.filevault;

import dcraft.filestore.CommonPath;
import dcraft.filestore.FileStoreFile;
import dcraft.hub.op.*;
import dcraft.log.Logger;
import dcraft.service.ServiceHub;
import dcraft.service.ServiceRequest;
import dcraft.stream.StreamFragment;
import dcraft.stream.StreamWork;
import dcraft.struct.RecordStruct;
import dcraft.struct.Struct;
import dcraft.struct.scalar.BooleanStruct;
import dcraft.task.Task;
import dcraft.task.TaskContext;
import dcraft.task.TaskHub;
import dcraft.task.TaskObserver;

import java.util.HashMap;

public class VaultUtil {
	static public void transfer(String vname, FileStoreFile upfile, CommonPath destpath, String token, OperationOutcomeStruct callback) throws OperatingContextException {
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

						VaultUtil.transfer(channel,
								StreamFragment.of(upfile.allocStreamSrc()),
								null,
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

			Object so = ((RecordStruct)centry).getFieldAsAny("Stream");

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
		HashMap<String, Struct> scache = OperationContext.getOrThrow().getSession().getCache();
		
		scache.put(token, BooleanStruct.of(true));
	}
}
