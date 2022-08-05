package dcraft.cms.meta;

import dcraft.db.DatabaseException;
import dcraft.db.fileindex.BasicFilter;
import dcraft.db.fileindex.FileIndexAdapter;
import dcraft.db.fileindex.IFilter;
import dcraft.db.proc.ExpressionResult;
import dcraft.filestore.CommonPath;
import dcraft.filevault.Vault;
import dcraft.hub.op.IVariableAware;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationOutcomeEmpty;
import dcraft.struct.RecordStruct;
import dcraft.struct.Struct;
import dcraft.task.IWork;
import dcraft.task.TaskContext;

import java.util.List;

public class ClearIndexWork implements IWork {
    static public ClearIndexWork of(CustomIndexAdapter adapter, ICustomIndexer indexer) {
        ClearIndexWork work = new ClearIndexWork();

        work.adapter = adapter;
        work.indexer = indexer;

        return work;
    }

    protected ICustomIndexer indexer = null;
    protected CustomIndexAdapter adapter = null;

    @Override
    public void run(TaskContext taskctx) throws OperatingContextException {
        this.adapter.clearCustomIndex();

        taskctx.returnEmpty();
    }
}
