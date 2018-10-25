package dcraft.filevault.work.steps;

import dcraft.filestore.CollectionSourceStream;
import dcraft.filestore.CommonPath;
import dcraft.filestore.FileCollection;
import dcraft.filestore.FileDescriptor;
import dcraft.filestore.FileStore;
import dcraft.filestore.local.LocalStore;
import dcraft.filestore.local.LocalStoreFile;
import dcraft.hub.app.ApplicationHub;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
import dcraft.log.Logger;
import dcraft.stream.ReturnOption;
import dcraft.stream.StreamWork;
import dcraft.stream.file.FileSlice;
import dcraft.stream.file.TransformFileStream;
import dcraft.struct.RecordStruct;
import dcraft.task.ChainWork;
import dcraft.task.TaskContext;
import dcraft.util.StringUtil;

public class DownloadDepositWork extends ChainWork {
	static public DownloadDepositWork of(String nodeid, String depositid, FileStore depositstore, RecordStruct manifest) {
		DownloadDepositWork work = new DownloadDepositWork();
		work.manifest = manifest;
		work.nodeid = nodeid;
		work.depositid = depositid;
		work.remote = depositstore;
		return  work;
	}
	
	protected String nodeid = null;
	protected String depositid = null;
	protected RecordStruct manifest = null;
	protected FileStore remote = null;
	
	@Override
	protected void init(TaskContext taskctx) throws OperatingContextException {
		if (! "Deposit".equals(this.manifest.getFieldAsString("Type"))) {
			Logger.error("Unknown deposit type: " + this.manifest.getFieldAsString("Type"));
			taskctx.returnEmpty();
			return;
		}
		
		int cnt = (int) this.manifest.getFieldAsInteger("SplitCount", 0);
		
		if (cnt < 0) {
			Logger.error("Invalid SplitCount: " + cnt);
			taskctx.returnEmpty();
			return;
		}
		
		// can be zero if only deletes are present
		if (cnt == 0) {
			taskctx.returnEmpty();
			return;
		}
		
		// TODO make sure we verify the .sig and - when we use it - the deposit
		
		LocalStore nodeDepositStore = LocalStore.of(ApplicationHub.getDeploymentPath().resolve("nodes/" + this.nodeid + "/deposits"));
		
		FileCollection finalfiles = new FileCollection();
		
		LocalStoreFile chkfile = nodeDepositStore.resolvePathToStore("/files/" + this.depositid + ".sig");
		
		if (! chkfile.exists())
			finalfiles.withFiles(this.remote.fileReference(CommonPath.from("/deposits/" + this.nodeid
					+ "/files/" + this.depositid + ".sig")));
		
		for (int i = 1; i <= cnt; i++) {
			String fname = "/files/" + this.depositid + ".tgzp-" + StringUtil.leftPad(i + "", 4, '0');
			
			LocalStoreFile chkfiled = nodeDepositStore.resolvePathToStore(fname);
			
			if (! chkfiled.exists())
				finalfiles.withFiles(this.remote.fileReference(CommonPath.from("/deposits/" + this.nodeid
						+ fname)));
		}
		
		if (finalfiles.getSize() == 0) {
			Logger.info("Deposit files already present.");
			taskctx.returnEmpty();
			return;
		}
		
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
				},
				nodeDepositStore.rootFolder().allocStreamDest()
		));
	
	}
}
