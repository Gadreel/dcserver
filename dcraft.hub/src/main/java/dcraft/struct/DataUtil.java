package dcraft.struct;

import dcraft.hub.ResourceHub;
import dcraft.hub.op.OperatingContextException;
import dcraft.struct.format.FormatResult;
import dcraft.struct.format.IFormatter;
import dcraft.struct.format.YesNoFormatter;
import dcraft.util.StringUtil;
import dcraft.xml.XNode;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public class DataUtil {
	static public Object format(Struct value, String format) throws OperatingContextException {
		if (StringUtil.isEmpty(format))
			return value;

		// convert scalars to objects
		if (value instanceof ScalarStruct)
			return DataUtil.format(((ScalarStruct) value).getGenericValue(), format);

		// null and composites pass through
		return DataUtil.format((Object) value, format);
	}
	
	static public Object format(Object value, String format) throws OperatingContextException {
		if (StringUtil.isEmpty(format))
			return value;
		
		return DataUtil.format(value, format.split("\\|"));
	}

	static public Object format(Struct value, String... formats) throws OperatingContextException {
		// convert scalars to objects
		if (value instanceof ScalarStruct)
			return DataUtil.format(((ScalarStruct) value).getGenericValue(), formats);

		// null and composites pass through
		return DataUtil.format((Object) value, formats);
	}
	
	static public Object format(Object value, String... formats) throws OperatingContextException {
		if (formats.length == 0)
			return value;
		
		for (String format : formats) {
			int pos = format.indexOf(':');
			
			if (pos == -1)
				continue;
			
			String op = format.substring(0, pos);
			String fmt = null;
			
			if (format.length() > pos + 1)
				fmt = format.substring(pos + 1);
		
			IFormatter formatter = ResourceHub.getResources().getScripts().getFormatter(op);	
			
			if (formatter != null) {
				FormatResult formatResult = formatter.format(value, op, fmt);
				
				value = formatResult.getResult();
				
				if (formatResult.isHalt())
					break;
			}
		}
		
		return value;
	}

	/*
		  ;
		  ;
		 format(table,field,val,format) i (table'["#")&(Domain'="") s table=table_"#"_Domain     ; support table instances
		  quit:format="" $$getTypeFor(^dcSchema($p(table,"#"),"Fields",field,"Type"))_val
		  quit:format="Tr" ScalarStr_$$tr^dcStrUtil("_enum_"_table_"_"_field_"_"_val)
		  ; TODO support date and number formatting, maybe str padding
		  quit ScalarStr_$$format^dcStrUtil(val,format)
		  ;
		  ;
		  ;
		 getTypeFor(type) quit:type="Time" ScalarTime
		  quit:type="Date" ScalarDate
		  quit:type="DateTime" ScalarDateTime
		  quit:type="Id" ScalarId
		  quit:type="Integer" ScalarInt
		  quit:type="Json" ScalarJson
		  quit:type="Decimal" ScalarDec
		  quit:type="BigInteger" ScalarBigInt
		  quit:type="BigDecimal" ScalarBigDec
		  quit:type="Number" ScalarNum
		  quit:type="Boolean" ScalarBool
		  quit:type="Binary" ScalarBin
		  quit:type="BigDateTime" ScalarBigDateTime
		  quit ScalarStr
		  ;
		  ;

	*/
	
}
