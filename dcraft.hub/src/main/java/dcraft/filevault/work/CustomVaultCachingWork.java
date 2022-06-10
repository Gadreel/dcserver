package dcraft.filevault.work;

import dcraft.cms.meta.CustomVaultUtil;
import dcraft.filestore.CommonPath;
import dcraft.filevault.TransactionBase;
import dcraft.filevault.TransactionFile;
import dcraft.filevault.Vault;
import dcraft.hub.ResourceHub;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationMarker;
import dcraft.hub.op.OperationOutcomeEmpty;
import dcraft.hub.resource.CustomVaultResource;
import dcraft.log.Logger;
import dcraft.task.IWork;
import dcraft.task.TaskContext;

import java.util.ArrayList;
import java.util.List;

public class CustomVaultCachingWork implements IWork {
    static public CustomVaultCachingWork of(Vault vault, TransactionBase tx) {
        CustomVaultCachingWork work = new CustomVaultCachingWork();

        work.vault = vault;
        work.tx = tx;

        return work;
    }

    protected Vault vault = null;
    protected TransactionBase tx = null;

    @Override
    public void run(TaskContext taskctx) throws OperatingContextException {
        try (OperationMarker om = OperationMarker.create()) {
            CustomVaultResource vaultResource = ResourceHub.getResources().getCustomVault();

            if (vaultResource != null) {
                List<CommonPath> deleteFiles = new ArrayList<>();

                for (TransactionFile file : this.tx.getDeletelist())
                    deleteFiles.add(file.getPath());

                List<CommonPath> updateFiles = new ArrayList<>();

                for (TransactionFile file : this.tx.getUpdateList())
                    updateFiles.add(file.getPath());

                CustomVaultUtil.updateFileCache(this.vault.getName(), vaultResource.getVaultInfo(this.vault.getName()), updateFiles, deleteFiles, new OperationOutcomeEmpty() {
                    @Override
                    public void callback() throws OperatingContextException {
                        taskctx.returnEmpty();
                    }
                });
            }
            else {
                Logger.error("Vault resources not found.");

                taskctx.returnEmpty();
            }
        }
        catch (Exception x) {
            Logger.error("Unable to process web file: " + x);

            taskctx.returnEmpty();
        }
    }
}
