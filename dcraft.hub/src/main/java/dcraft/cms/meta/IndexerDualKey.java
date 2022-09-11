package dcraft.cms.meta;

import dcraft.db.DatabaseException;
import dcraft.filestore.CommonPath;
import dcraft.hub.op.OperatingContextException;
import dcraft.struct.BaseStruct;
import dcraft.struct.ListStruct;
import dcraft.struct.RecordStruct;
import dcraft.struct.ScalarStruct;
import dcraft.util.StringUtil;

import java.util.List;

public class IndexerDualKey extends BasicCustomIndexer {
    @Override
    public void indexRecord(String vault, CommonPath path, RecordStruct values, RecordStruct oldvalues) throws OperatingContextException {
        if (StringUtil.isNotEmpty(this.firstKey) && StringUtil.isNotEmpty(this.secondKey)) {
            // remove old values
            // TODO delta remove and set only what is needed - diff of old and new

            if (oldvalues != null) {
                BaseStruct value1 = oldvalues.getField(this.firstKey);
                BaseStruct value2 = oldvalues.getField(this.secondKey);

                if (value1 instanceof ListStruct) {
                    ListStruct list = (ListStruct) value1;

                    for (int i = 0; i < list.size(); i++) {
                        BaseStruct entry = list.getItem(i);

                        if (entry instanceof ScalarStruct) {
                            this.deleteSecond((ScalarStruct) entry, value2, vault, path);
                        }
                    }
                }
                else if (value1 instanceof ScalarStruct) {
                    this.deleteSecond((ScalarStruct) value1, value2, vault, path);
                }
            }

            // set new values

            BaseStruct value1 = values.getField(this.firstKey);
            BaseStruct value2 = values.getField(this.secondKey);

            if (value1 instanceof ListStruct) {
                ListStruct list = (ListStruct) value1;

                for (int i = 0; i < list.size(); i++) {
                    BaseStruct entry = list.getItem(i);

                    if (entry instanceof ScalarStruct) {
                        this.setSecond((ScalarStruct) entry, value2, vault, path);
                    }
                }
            }
            else if (value1 instanceof ScalarStruct) {
                this.setSecond((ScalarStruct) value1, value2, vault, path);
            }
        }
    }

    public void setSecond(ScalarStruct value1, BaseStruct value2, String vault, CommonPath path) throws OperatingContextException {
        if (value2 instanceof ListStruct) {
            ListStruct list = (ListStruct) value2;

            for (int i = 0; i < list.size(); i++) {
                BaseStruct entry = list.getItem(i);

                if (entry instanceof ScalarStruct) {
                    this.setIndex(value1, (ScalarStruct) entry, vault, path);
                }
            }
        }
        else if (value2 instanceof ScalarStruct) {
            this.setIndex(value1, (ScalarStruct) value2, vault, path);
        }
    }

    public void setIndex(ScalarStruct value1, ScalarStruct value2, String vault, CommonPath path) throws OperatingContextException {
        try {
            List<Object> keys = CustomIndexUtil.pathToIndex(index.getFieldAsString("Alias"));

            keys.add(this.firstDataType.toIndex(value1, this.locale.getDefaultLocale()));        // meta indexing is not meant for locale work, always use site default locale
            keys.add(this.secondDataType.toIndex(value2, this.locale.getDefaultLocale()));        // meta indexing is not meant for locale work, always use site default locale
            keys.add(vault);
            keys.add(path.toString());
            keys.add(1);

            this.adapter.getRequest().getInterface().set(keys.toArray());
        }
        catch (DatabaseException x) {
            System.out.println("database write error - meta custom index");
        }


        //System.out.println(keys.toString());
    }

    public void deleteSecond(ScalarStruct value1, BaseStruct value2, String vault, CommonPath path) throws OperatingContextException {
        if (value2 instanceof ListStruct) {
            ListStruct list = (ListStruct) value2;

            for (int i = 0; i < list.size(); i++) {
                BaseStruct entry = list.getItem(i);

                if (entry instanceof ScalarStruct) {
                    this.deleteIndex(value1, (ScalarStruct) entry, vault, path);
                }
            }
        }
        else if (value2 instanceof ScalarStruct) {
            this.deleteIndex(value1, (ScalarStruct) value2, vault, path);
        }
    }

    public void deleteIndex(ScalarStruct value1, ScalarStruct value2, String vault, CommonPath path) throws OperatingContextException {
        try {
            List<Object> keys = CustomIndexUtil.pathToIndex(index.getFieldAsString("Alias"));

            keys.add(this.firstDataType.toIndex(value1, this.locale.getDefaultLocale()));        // meta indexing is not meant for locale work, always use site default locale
            keys.add(this.secondDataType.toIndex(value2, this.locale.getDefaultLocale()));        // meta indexing is not meant for locale work, always use site default locale
            keys.add(vault);
            keys.add(path.toString());

            this.adapter.getRequest().getInterface().kill(keys.toArray());
        }
        catch (DatabaseException x) {
            System.out.println("database write error - meta custom index");
        }


        //System.out.println(keys.toString());
    }
}
