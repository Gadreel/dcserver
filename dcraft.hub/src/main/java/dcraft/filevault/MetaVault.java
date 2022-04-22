package dcraft.filevault;

import dcraft.cms.feed.db.FeedUtilDb;
import dcraft.db.BasicRequestContext;
import dcraft.db.IConnectionManager;
import dcraft.db.tables.TablesAdapter;
import dcraft.filevault.work.FeedSearchWork;
import dcraft.hub.ResourceHub;
import dcraft.hub.op.OperatingContextException;
import dcraft.task.TaskHub;
import dcraft.web.ui.UIUtil;

public class MetaVault extends FileStoreVault {
    @Override
    public void processTransaction(TransactionBase tx) throws OperatingContextException {
        // Feeds needs to be a local store with Expand mode

        // process moves the files
        super.processTransaction(tx);

        // TODO respond to transactions and do the indexing

        /*
        IConnectionManager connectionManager = ResourceHub.getResources().getDatabases().getDatabase();

        TablesAdapter adapter = TablesAdapter.of(BasicRequestContext.of(connectionManager.allocateAdapter()));

        for (TransactionFile file : tx.getDeletelist()) {
            FeedUtilDb.deleteFeedIndex(adapter, file.getPath());
        }

        // clean list does not matter here

        for (TransactionFile file : tx.getUpdateList()) {
            FeedUtilDb.updateFeedIndex(adapter, file.getPath());		// TODO ideally we'd pass the file date in too for the dcmModified field

            TaskHub.submit(
                    UIUtil.mockWebRequestTask(this.tenant, this.site, "Feed file search indexing")
                            .withWork(FeedSearchWork.of(this, file.getPath()))
            );
        }

         */
    }

}
