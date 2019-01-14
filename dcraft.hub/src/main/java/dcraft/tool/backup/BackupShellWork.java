package dcraft.tool.backup;

import dcraft.db.Constants;
import dcraft.db.IConnectionManager;
import dcraft.db.request.update.UpdateRecordRequest;
import dcraft.filestore.CollectionSourceStream;
import dcraft.filestore.CommonPath;
import dcraft.filestore.FileCollection;
import dcraft.filestore.FileStoreFile;
import dcraft.filestore.local.LocalStore;
import dcraft.filevault.Transaction;
import dcraft.hub.ResourceHub;
import dcraft.hub.app.ApplicationHub;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationOutcome;
import dcraft.hub.op.OperationOutcomeEmpty;
import dcraft.hub.op.OperationOutcomeStruct;
import dcraft.log.Logger;
import dcraft.service.ServiceHub;
import dcraft.stream.StreamWork;
import dcraft.struct.ListStruct;
import dcraft.struct.RecordStruct;
import dcraft.struct.Struct;
import dcraft.task.ShellWork;
import dcraft.task.StateWork;
import dcraft.task.StateWorkStep;
import dcraft.task.TaskContext;
import dcraft.util.TimeUtil;

public class BackupShellWork extends StateWork {
	protected StateWorkStep shellStep = null;
	protected StateWorkStep notify = null;
	
	protected Struct tparams = null;
	protected int logpos = 0;
	
	@Override
	public void prepSteps(TaskContext trun) throws OperatingContextException {
		this.withSteps(
				shellStep = StateWorkStep.of("Shell to backup.sh", this::copyFiles),
				notify = StateWorkStep.of("Notify", this::notify)
		);
		
		tparams = trun.getTask().getParams();
	}
	
	public StateWorkStep copyFiles(TaskContext trun) throws OperatingContextException {
		String shellpath = ApplicationHub.getDeploymentNodePath().toString() + "/util/backup.sh";

		RecordStruct shellParams = ShellWork.buildTaskParams(shellpath, ".", 20000L);
		
		logpos = trun.getController().logMarker();
		trun.getTask().withParams(shellParams);
		
		return this.chainThenNext(trun, ShellWork.work());
	}
	
	public StateWorkStep notify(TaskContext trun) throws OperatingContextException {
		trun.getTask().withParams(tparams);
		
		int files = 0;
		
		ListStruct msgs = trun.getController().getMessages(logpos, trun.getController().logMarker());
		
		for (int i = 0; i < msgs.size(); i++) {
			String msg = msgs.getItemAsRecord(i).getFieldAsString("Message");
			
			// TODO config
			if (msg.startsWith("dcserver/"))
				files++;
		}
		
		BackupUtil.notifyProgress(ApplicationHub.getDeployment() + " : " + ApplicationHub.getNodeId() + " : shell backup done - files backed up: " + files);
		
		return StateWorkStep.STOP;
	}
}
