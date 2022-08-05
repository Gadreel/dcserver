package dcraft.cms.meta;

import dcraft.db.BasicRequestContext;
import dcraft.db.DatabaseException;
import dcraft.db.IConnectionManager;
import dcraft.db.fileindex.BasicFilter;
import dcraft.db.fileindex.FileIndexAdapter;
import dcraft.db.fileindex.IFilter;
import dcraft.db.proc.ExpressionResult;
import dcraft.filestore.CommonPath;
import dcraft.filevault.Vault;
import dcraft.hub.ResourceHub;
import dcraft.hub.op.IVariableAware;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationOutcomeEmpty;
import dcraft.struct.BaseStruct;
import dcraft.struct.RecordStruct;
import dcraft.struct.Struct;
import dcraft.struct.builder.BuilderStateException;
import dcraft.task.IWork;
import dcraft.task.TaskContext;

import java.util.List;

public class BasicIndexVaultWork implements IWork {
    static public BasicIndexVaultWork of(String vaultName, CustomIndexAdapter adapter, BasicIndexMap mapper, ICustomIndexer indexer) {
        BasicIndexVaultWork work = new BasicIndexVaultWork();

        work.vaultName = vaultName;
        work.adapter = adapter;
        work.mapper = mapper;
        work.indexer = indexer;

        return work;
    }

    protected String vaultName = null;
    protected BasicIndexMap mapper = null;
    protected ICustomIndexer indexer = null;
    protected CustomIndexAdapter adapter = null;

    @Override
    public void run(TaskContext taskctx) throws OperatingContextException {
        IFilter recordFilter = new BasicFilter() {
            @Override
            public ExpressionResult check(FileIndexAdapter adapter, IVariableAware scope, Vault vault, CommonPath path, RecordStruct file) throws OperatingContextException {
                try {
                    List<Object> entrykeys = FileIndexAdapter.pathToIndex(vault, path);

                    entrykeys.add("Data");

                    RecordStruct data = Struct.objectToRecord(adapter.getRequest().getInterface().get(entrykeys.toArray()));

                    if (data != null) {
                        data = mapper.mapRecord(data);

                        indexer.indexRecord(vault.getName(), path, data, null);
                    }
                }
                catch (DatabaseException x) {
                    // TODO
                    System.out.println("database error: " + x);
                }

                return ExpressionResult.accepted();
            }
        };

        // TODO set up recordFilter
        // run map
        // if not empty then index

        CustomVaultUtil.interiateFileCache(vaultName, recordFilter, new OperationOutcomeEmpty() {
            @Override
            public void callback() throws OperatingContextException {
                taskctx.returnEmpty();
            }
        });
    }
}
