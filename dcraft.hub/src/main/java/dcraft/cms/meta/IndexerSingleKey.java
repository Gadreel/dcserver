package dcraft.cms.meta;

import dcraft.db.DatabaseException;
import dcraft.filestore.CommonPath;
import dcraft.hub.op.OperatingContextException;
import dcraft.log.Logger;
import dcraft.struct.BaseStruct;
import dcraft.struct.ListStruct;
import dcraft.struct.RecordStruct;
import dcraft.struct.ScalarStruct;
import dcraft.task.ChainWork;
import dcraft.util.StringUtil;

import java.util.List;

public class IndexerSingleKey extends BasicCustomIndexer {
    @Override
    public void indexRecord(String vault, CommonPath path, RecordStruct values, RecordStruct oldvalues) throws OperatingContextException {
        if (StringUtil.isNotEmpty(this.firstKey)) {
            // remove old values
            // TODO delta remove and set only what is needed - diff of old and new

            //System.out.println("remove old");

            if (oldvalues != null) {
                //System.out.println("old: " + oldvalues);

                BaseStruct value = oldvalues.getField(this.firstKey);

                if (value instanceof ListStruct) {
                    ListStruct list = (ListStruct) value;

                    for (int i = 0; i < list.size(); i++) {
                        BaseStruct entry = list.getItem(i);

                        if (entry instanceof ScalarStruct) {
                            this.deleteIndex((ScalarStruct) entry, vault, path);
                        }
                    }
                }
                else if (value instanceof ScalarStruct) {
                    this.deleteIndex((ScalarStruct) value, vault, path);
                }
            }

            // set new values

            //System.out.println("set old");

            BaseStruct value = values.getField(this.firstKey);

            if (value instanceof ListStruct) {
                ListStruct list = (ListStruct) value;

                for (int i = 0; i < list.size(); i++) {
                    BaseStruct entry = list.getItem(i);

                    if (entry instanceof ScalarStruct) {
                        this.setIndex((ScalarStruct) entry, vault, path);
                    }
                }
            }
            else if (value instanceof ScalarStruct) {
                this.setIndex((ScalarStruct) value, vault, path);
            }

            System.out.println("indexed");
        }
    }

    public void setIndex(ScalarStruct value, String vault, CommonPath path) throws OperatingContextException {
        try {
            List<Object> keys = CustomIndexUtil.pathToIndex(index.getFieldAsString("Alias"));

            keys.add(this.firstDataType.toIndex(value, this.locale.getDefaultLocale()));        // meta indexing is not meant for locale work, always use site default locale
            keys.add(vault);
            keys.add(path.toString());
            keys.add(1);

            this.adapter.getRequest().getInterface().set(keys.toArray());
        }
        catch (DatabaseException x) {
            System.out.println("database write error - meta custom index");
        }

        // System.out.println(keys.toString());
    }

    public void deleteIndex(ScalarStruct value, String vault, CommonPath path) throws OperatingContextException {
        try {
            List<Object> keys = CustomIndexUtil.pathToIndex(index.getFieldAsString("Alias"));

            keys.add(this.firstDataType.toIndex(value, this.locale.getDefaultLocale()));        // meta indexing is not meant for locale work, always use site default locale
            keys.add(vault);
            keys.add(path.toString());

            this.adapter.getRequest().getInterface().kill(keys.toArray());
        }
        catch (DatabaseException x) {
            System.out.println("database write error - meta custom index");
        }

        // System.out.println(keys.toString());
    }
}
