package dcraft.filevault;

import dcraft.filevault.work.CustomVaultCachingWork;
import dcraft.hub.op.OperatingContextException;
import dcraft.task.Task;
import dcraft.task.TaskHub;

public class CustomLocalVault extends FileStoreVault {
    @Override
    public void processTransaction(TransactionBase tx) throws OperatingContextException {
        // process moves the files
        super.processTransaction(tx);

        TaskHub.submit(
                Task.ofSubtask("Custom vault search indexing", "INDX")
                        .withWork(CustomVaultCachingWork.of(this, tx))
        );
    }
}
