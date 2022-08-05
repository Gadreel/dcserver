package dcraft.cms.meta;

import dcraft.db.BasicRequestContext;
import dcraft.db.IConnectionManager;
import dcraft.filestore.CommonPath;
import dcraft.hub.ResourceHub;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
import dcraft.locale.LocaleResource;
import dcraft.log.Logger;
import dcraft.schema.DataType;
import dcraft.schema.SchemaResource;
import dcraft.struct.ListStruct;
import dcraft.struct.RecordStruct;
import dcraft.struct.ScalarStruct;
import dcraft.task.ChainWork;

import java.util.List;

abstract public class BasicCustomIndexer implements ICustomIndexer {
    protected RecordStruct index = null;
    protected CustomIndexAdapter adapter = null;
    protected LocaleResource locale = null;
    protected String firstKey = null;
    protected DataType firstDataType = null;
    protected String secondKey = null;
    protected DataType secondDataType = null;

    @Override
    public void init(RecordStruct index, CustomIndexAdapter adapter) throws OperatingContextException {
        this.index = index;
        this.adapter = adapter;

        // meta indexing is not meant for locale specific data/fields, always use site default locale
        this.locale = OperationContext.getOrThrow().getTenant().getResources().getLocale();

        RecordStruct config = index.getFieldAsRecord("IndexHandlerConfig");

        if (config == null) {
            Logger.error("Single Key custom index missing handler config");
            return;
        }

        SchemaResource schemares = ResourceHub.getResources().getSchema();

        if (config.isNotFieldEmpty("FirstKey")) {
            this.firstKey = config.getFieldAsString("FirstKey");
            this.firstDataType = schemares.getType(config.getFieldAsString("FirstType", "dcMetaString"));
        }

        if (config.isNotFieldEmpty("SecondKey")) {
            this.secondKey = config.getFieldAsString("SecondKey");
            this.secondDataType = schemares.getType(config.getFieldAsString("SecondType", "dcMetaString"));
        }
    }

    @Override
    public String getIndexAlias() {
        return this.index.getFieldAsString("Alias");
    }

    @Override
    public void reindexWorkPrep(ChainWork workChain) throws OperatingContextException {
        RecordStruct config = index.getFieldAsRecord("IndexHandlerConfig");

        if (config == null) {
            Logger.error("Single Key custom index missing handler config");
            return;
        }

        ListStruct list = config.getFieldAsList("Maps");

        if (list == null) {
            Logger.error("Single Key custom index missing handler maps");
            return;
        }

        workChain.then(ClearIndexWork.of(this.adapter, this));

        for (int i = 0; i < list.getSize(); i++) {
            RecordStruct map = list.getItemAsRecord(i);

            BasicIndexMap mapper = BasicIndexMap.of(config, map);

            workChain.then(BasicIndexVaultWork.of(map.getFieldAsString("Vault"), this.adapter, mapper, this));
        }
    }
}
