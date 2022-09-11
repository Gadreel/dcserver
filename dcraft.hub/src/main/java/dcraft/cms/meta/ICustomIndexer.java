package dcraft.cms.meta;

import dcraft.filestore.CommonPath;
import dcraft.hub.op.OperatingContextException;
import dcraft.struct.RecordStruct;
import dcraft.task.ChainWork;

public interface ICustomIndexer {
    void init(RecordStruct config, CustomIndexAdapter adapter) throws OperatingContextException;
    void indexRecord(String vault, CommonPath path, RecordStruct values, RecordStruct oldvalues) throws OperatingContextException;
    void reindexWorkPrep(ChainWork workChain) throws OperatingContextException;
    BasicIndexMap getIndexMap(String vault);
    String getIndexAlias();
}
