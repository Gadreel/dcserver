package dcraft.cms.meta;

import dcraft.db.fileindex.IFilter;
import dcraft.filestore.CommonPath;
import dcraft.hub.op.IVariableAware;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationOutcomeEmpty;
import dcraft.hub.op.OperationOutcomeStruct;
import dcraft.struct.RecordStruct;
import dcraft.task.ChainWork;

import java.util.Iterator;

public interface ICustomIterator {
    void init(RecordStruct config, CustomIndexAdapter adapter, RecordStruct params, IFilter recordFilter, IVariableAware scope) throws OperatingContextException;
    void search(OperationOutcomeEmpty callback) throws OperatingContextException;
    String getIndexAlias();
}
