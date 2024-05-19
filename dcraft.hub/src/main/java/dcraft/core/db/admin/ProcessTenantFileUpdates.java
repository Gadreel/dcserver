package dcraft.core.db.admin;

import dcraft.core.work.admin.FileUpdatesWork;
import dcraft.db.ICallContext;
import dcraft.db.proc.IStoredProc;
import dcraft.filestore.CommonPath;
import dcraft.filevault.FileStoreVault;
import dcraft.filevault.IndexTransaction;
import dcraft.filevault.TransactionFile;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
import dcraft.hub.op.OperationOutcomeStruct;
import dcraft.hub.op.UserContext;
import dcraft.log.Logger;
import dcraft.struct.ListStruct;
import dcraft.struct.RecordStruct;
import dcraft.task.*;
import dcraft.tenant.Site;
import dcraft.tenant.Tenant;
import dcraft.tenant.TenantHub;
import dcraft.tool.backup.BackupWork;

public class ProcessTenantFileUpdates implements IStoredProc {
	@Override
	public void execute(ICallContext request, OperationOutcomeStruct callback) throws OperatingContextException {
		RecordStruct files = request.getDataAsRecord();

		FileUpdatesWork work = FileUpdatesWork.of(files.getFieldAsList("Updates"), files.getFieldAsList("Deletes"));

		TaskHub.submit(Task.ofSubContext()
					.withTitle("Process list of updated tenant files")
					.withWork(work),
				new TaskObserver() {
					@Override
					public void callback(TaskContext task) {
						// don't want on backup
						callback.returnEmpty();
					}
				}
		);
	}
}
