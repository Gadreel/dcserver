package dcraft.cms.store;

import dcraft.hub.op.OperatingContextException;
import dcraft.struct.ListStruct;
import dcraft.struct.RecordStruct;

import java.math.BigDecimal;

public interface ICalcItemPrice {
    BigDecimal calc(RecordStruct order, RecordStruct item, ListStruct options) throws OperatingContextException;
}
