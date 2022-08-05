package dcraft.cms.meta;

import dcraft.db.BasicRequestContext;
import dcraft.db.DatabaseException;
import dcraft.db.IConnectionManager;
import dcraft.db.fileindex.BasicFilter;
import dcraft.db.fileindex.FileIndexAdapter;
import dcraft.db.fileindex.Filter.StandardAccess;
import dcraft.db.fileindex.Filter.Tags;
import dcraft.db.fileindex.Filter.Term;
import dcraft.db.fileindex.IFilter;
import dcraft.db.proc.ExpressionResult;
import dcraft.filestore.CommonPath;
import dcraft.filevault.Vault;
import dcraft.hub.ResourceHub;
import dcraft.hub.op.*;
import dcraft.log.Logger;
import dcraft.struct.BaseStruct;
import dcraft.struct.ListStruct;
import dcraft.struct.RecordStruct;
import dcraft.struct.Struct;
import dcraft.struct.builder.BuilderStateException;
import dcraft.struct.builder.ICompositeBuilder;
import dcraft.struct.builder.ObjectBuilder;
import dcraft.task.ChainWork;
import dcraft.task.TaskContext;
import dcraft.task.TaskHub;
import dcraft.task.TaskObserver;
import dcraft.tenant.Site;

import java.util.ArrayList;
import java.util.List;

public class CustomIndexUtil {
    static public List<Object> pathToIndex(String indexName) throws OperatingContextException {
        List<Object> indexkeys = new ArrayList<>();

        Site site = OperationContext.getOrThrow().getSite();

        indexkeys.add(site.getTenant().getAlias());
        indexkeys.add("dcMetaIndex");
        indexkeys.add(site.getAlias());
        indexkeys.add(indexName);

        return indexkeys;
    }

    static public void updateCustomIndexAll(String indexName, OperationOutcomeEmpty callback) throws OperatingContextException {
        RecordStruct index = ResourceHub.getResources().getCustomIndexing().getIndexInfo(indexName);

        if (index == null) {
            Logger.error("Custom Index not found: " + indexName);
            callback.returnEmpty();
            return;
        }

        String handlerType = index.getFieldAsString("IndexHandler", "SingleKey");

        ICustomIndexer indexer = null;

        // TODO make customizable indexers available
        if ("SingleKey".equals(handlerType))
            indexer = new IndexerSingleKey();
        else if ("DualKey".equals(handlerType))
            indexer = new IndexerDualKey();

        if (indexer == null) {
            Logger.error("Custom Index has invalid handler: " + indexName);
            callback.returnEmpty();
            return;
        }

        IConnectionManager connectionManager = ResourceHub.getResources().getDatabases().getDatabase();

        CustomIndexAdapter adapter = CustomIndexAdapter.of(BasicRequestContext.of(connectionManager.allocateAdapter()), indexName);

        indexer.init(index, adapter);

        ChainWork chain = ChainWork.chain();

        indexer.reindexWorkPrep(chain);

        TaskHub.submit(chain, new TaskObserver() {
            @Override
            public void callback(TaskContext task) {
                callback.returnEmpty();
            }
        });
    }

    /*
        params: {
            IndexName: nnnn
            VaultsAllowed: [ nnn, nnn, nnn ]
            VaultsExcluded: [ nnn, nnn, nnn ]
            FeedsAllowed: [ nnn, nnn, nnn ]             // only for "pages" vault
            FeedsExcluded: [ nnn, nnn, nnn ]
            SearchKeys: [
                {
                    KeyName: nnnn
                    Mode: [list|range]
                    Values: [ nnn, nnn, nnn ]       // if list
                    From: nnnn                      // if range
                    To: nnnn                        // if range
                }
            ]
        }
     */
    static public void searchCustomIndex(RecordStruct params, IFilter recordFilter, OperationOutcomeEmpty callback) throws OperatingContextException {
        System.out.println("Search CI a");

        String indexName = params.getFieldAsString("IndexName");

        RecordStruct index = ResourceHub.getResources().getCustomIndexing().getIndexInfo(indexName);

        if (index == null) {
            Logger.error("Custom Index not found: " + indexName);
            callback.returnEmpty();
            return;
        }

        String handlerType = index.getFieldAsString("IndexHandler", "SingleKey");

        ICustomIterator iterator = null;

        // TODO make customizable indexers available
        if ("SingleKey".equals(handlerType))
            iterator = new IteratorSingleKey();
        else if ("DualKey".equals(handlerType))
            iterator = new IteratorDualKey();

        if (iterator == null) {
            Logger.error("Custom Index has invalid handler/iterator: " + indexName);
            callback.returnEmpty();
            return;
        }

        System.out.println("Search CI b");

        IConnectionManager connectionManager = ResourceHub.getResources().getDatabases().getDatabase();

        CustomIndexAdapter adapter = CustomIndexAdapter.of(BasicRequestContext.of(connectionManager.allocateAdapter()), indexName);

        CustomScope scope = CustomScope.of(OperationContext.getOrThrow());

        System.out.println("Search CI c");

        iterator.init(index, adapter, params, recordFilter, scope);

        System.out.println("Search CI d");

        iterator.search(callback);
    }
}
