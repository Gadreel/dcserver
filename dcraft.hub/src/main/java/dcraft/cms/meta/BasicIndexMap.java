package dcraft.cms.meta;

import dcraft.hub.op.OperatingContextException;
import dcraft.struct.BaseStruct;
import dcraft.struct.DataUtil;
import dcraft.struct.RecordStruct;
import dcraft.struct.Struct;
import dcraft.util.StringUtil;

public class BasicIndexMap {
    static public BasicIndexMap of(RecordStruct config, RecordStruct map) {
        BasicIndexMap mapper = new BasicIndexMap();

        if (config.isNotFieldEmpty("FirstKey"))
            mapper.destFirstKey = config.getFieldAsString("FirstKey");

        if (config.isNotFieldEmpty("SecondKey"))
            mapper.destSecondKey = config.getFieldAsString("SecondKey");

        if (map.isNotFieldEmpty("FirstField"))
            mapper.srcFirstField = map.getFieldAsString("FirstField");

        if (map.isNotFieldEmpty("SecondField"))
            mapper.srcSecondField = map.getFieldAsString("SecondField");

        if (map.isNotFieldEmpty("FirstFormatter"))
            mapper.srcFirstFormatter = map.getFieldAsString("FirstFormatter");

        if (map.isNotFieldEmpty("SecondFormatter"))
            mapper.srcSecondFormatter = map.getFieldAsString("SecondFormatter");

        return mapper;
    }

    protected String destFirstKey = null;
    protected String destSecondKey = null;
    protected String srcFirstField = null;
    protected String srcSecondField = null;
    protected String srcFirstFormatter = null;
    protected String srcSecondFormatter = null;

    public RecordStruct mapRecord(RecordStruct source) throws OperatingContextException {
        RecordStruct dest = RecordStruct.record();

        if (source != null) {
            if (StringUtil.isNotEmpty(this.srcFirstField) && StringUtil.isNotEmpty(this.destFirstKey)) {
                BaseStruct value = source.getField(this.srcFirstField);

                if (StringUtil.isNotEmpty(this.srcFirstFormatter))
                    value = Struct.objectToStruct(DataUtil.format(value, this.srcFirstFormatter));

                dest.with(this.destFirstKey, value);
            }

            if (StringUtil.isNotEmpty(this.srcSecondField) && StringUtil.isNotEmpty(this.destSecondKey)) {
                BaseStruct value = source.getField(this.srcSecondField);

                if (StringUtil.isNotEmpty(this.srcSecondFormatter))
                    value = Struct.objectToStruct(DataUtil.format(value, this.srcSecondFormatter));

                dest.with(this.destSecondKey, value);
            }
        }

        return dest;
    }
}
