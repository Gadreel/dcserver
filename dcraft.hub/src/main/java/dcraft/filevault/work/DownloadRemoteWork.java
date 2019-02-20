package dcraft.filevault.work;

import dcraft.filestore.CommonPath;
import dcraft.filestore.FileStore;
import dcraft.filestore.local.LocalStore;
import dcraft.filestore.local.LocalStoreFile;
import dcraft.filevault.DepositHub;
import dcraft.filevault.Transaction;
import dcraft.filevault.Vault;
import dcraft.filevault.VaultUtil;
import dcraft.hub.app.ApplicationHub;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationOutcomeEmpty;
import dcraft.log.Logger;
import dcraft.stream.StreamFragment;
import dcraft.stream.StreamWork;
import dcraft.struct.ListStruct;
import dcraft.struct.RecordStruct;
import dcraft.task.StateWork;
import dcraft.task.StateWorkStep;
import dcraft.task.TaskContext;
import dcraft.util.IOUtil;
import dcraft.util.StringUtil;

import java.io.BufferedInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;

public class DownloadRemoteWork extends StateWork {
	static public DownloadRemoteWork of(String vault, ListStruct files, String token) {
		DownloadRemoteWork work = new DownloadRemoteWork();
		work.vaultname = vault;
		work.token = token;
		work.files = files;
		return work;
	}
	
	protected String vaultname = null;
	protected ListStruct files = null;
	protected String token = null;
	protected int filepos = 0;
	
	protected Vault vault = null;
	protected Transaction tx = null;
	
	protected StateWorkStep downloadFile = null;
	protected StateWorkStep commit = null;
	protected StateWorkStep done = null;
	
	@Override
	public void prepSteps(TaskContext trun) throws OperatingContextException {
		this.vault = trun.getSite().getVault(this.vaultname);
		
		if (this.vault == null) {
			Logger.error("Unable to find vault");
			return;
		}
		
		String txid = Transaction.createTransactionId();
		this.tx = this.vault.buildUpdateTransaction(txid, null);
		
		VaultUtil.setSessionToken(this.token, txid);
		
		this
				.withStep(downloadFile = StateWorkStep.of("download file", this::downloadFile))
				.withStep(commit = StateWorkStep.of("commit tx", this::commit))
				.withStep(done = StateWorkStep.of("Done", this::done));
	}
	
	public StateWorkStep downloadFile(TaskContext trun) throws OperatingContextException {
		if (this.filepos >= this.files.size())
			return this.commit;
		
		trun.touch();
		
		RecordStruct filerec = this.files.getItemAsRecord(this.filepos);
			
		Logger.info("Downloading file: " + filerec.getFieldAsString("Source"));
		
		Path rpath = tx.getFolder().resolvePath(CommonPath.from(filerec.getFieldAsString("Destination")));
		
		try {
			Files.createDirectories(rpath.getParent());
		}
		catch (IOException x) {
			Logger.error("Unable to download: " + x);
		}
		
		try (BufferedInputStream in = new BufferedInputStream(new URL(filerec.getFieldAsString("Source")).openStream());
			FileOutputStream fileOutputStream = new FileOutputStream(rpath.toFile())) {
			
			byte dataBuffer[] = new byte[1024];
			int bytesRead;
			
			while ((bytesRead = in.read(dataBuffer, 0, 1024)) != -1) {
				fileOutputStream.write(dataBuffer, 0, bytesRead);
			}
			
			fileOutputStream.flush();
		}
		catch (IOException x) {
			Logger.error("Unable to download: " + x);
		}
		
		this.filepos++;
		
		return StateWorkStep.REPEAT;
	}
	
	public StateWorkStep commit(TaskContext trun) throws OperatingContextException {
		tx.commitTransaction(new OperationOutcomeEmpty() {
			@Override
			public void callback() throws OperatingContextException {
				DownloadRemoteWork.this.transition(trun, done);
			}
		});
		
		return StateWorkStep.WAIT;
	}
	
	public StateWorkStep done(TaskContext trun) throws OperatingContextException {
		VaultUtil.clearSessionToken(token);
		
		Logger.info("Files downloaded");
		
		return StateWorkStep.STOP;
	}
}
