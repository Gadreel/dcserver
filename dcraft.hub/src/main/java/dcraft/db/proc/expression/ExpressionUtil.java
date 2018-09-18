package dcraft.db.proc.expression;

import dcraft.db.proc.IExpression;
import dcraft.db.util.ByteUtil;
import dcraft.hub.ResourceHub;
import dcraft.hub.op.OperatingContextException;
import dcraft.locale.IndexInfo;
import dcraft.log.Logger;
import dcraft.schema.DataType;
import dcraft.schema.DbExpression;
import dcraft.schema.DbField;
import dcraft.schema.SchemaResource;
import dcraft.struct.ListStruct;
import dcraft.struct.RecordStruct;
import dcraft.struct.Struct;
import dcraft.util.StringUtil;

import java.util.ArrayList;
import java.util.List;

public class ExpressionUtil {
	static public int compare(List<byte[]> a, List<byte[]> b) {
		if ((a == null) && (b == null))
			return 0;
		
		if (a == null)
			return -1;
		
		if (b == null)
			return 1;
		
		int max = Math.max(a.size(), b.size());
		
		for (int i = 0; i < max; i++) {
			if (i >= a.size())
				return -1;
			
			if (i >= b.size())
				return 1;
			
			int comp = ByteUtil.compareKeys(a.get(i), b.get(i));
			
			if ((comp == 0) && (i < max - 1))
				continue;
			
			// first compare that is differs wins
			return comp;
		}
		
		// must be equal here
		return 0;
	}
	
	static public boolean any(List<byte[]> a, List<byte[]> b) {
		if ((a == null) && (b == null))
			return true;
		
		if (a == null)
			return false;
		
		if (b == null)
			return false;

		for (int i = 0; i < a.size(); i++) {
			for (int i2 = 0; i2 < b.size(); i2++) {
				int comp = ByteUtil.compareKeys(a.get(i), b.get(i2));

				if (comp == 0)
					return true;
			}
		}
		
		return false;
	}
	
	static public IExpression initExpression(String table, RecordStruct where) throws OperatingContextException {
		String expressionName = where.getFieldAsString("Expression");
		
		DbExpression proc = ResourceHub.getResources().getSchema().getDbExpression(expressionName);
		
		if (proc == null) {
			Logger.error("Missing expression: " + proc);
			return null;
		}
		
		String spname = proc.execute;		// TODO find class name for request.getOp()
		
		IExpression sp = (IExpression) ResourceHub.getResources().getClassLoader().getInstance(spname);
		
		if (sp == null) {
			Logger.error("Cannot create expression: " + expressionName);
			return null;
		}
		
		sp.init(table, where);
		
		where.with("_Expression", sp);
		
		return sp;
	}
	
	static public List<byte[]> loadSearchValues(RecordStruct pdef, FieldInfo field, String locale, String pre, String post) throws OperatingContextException {
		if (pdef == null)
			return null;
		
		Struct val = pdef.getField("Value");
		
		if (val == null)
			return null;
		
		ArrayList<byte[]> vl = new ArrayList<>();
		
		if (val instanceof ListStruct) {
			ListStruct lval = (ListStruct) val;
			
			for (int i2 = 0; i2 < lval.size(); i2++) {
				ExpressionUtil.loadSearchValue(vl, field, lval.getItem(i2), locale, pre, post);
			}
		}
		else {
			ExpressionUtil.loadSearchValue(vl, field, val, locale, pre, post);
		}
		
		return vl;
	}
	
	static public void loadSearchValue(List<byte[]> vl, FieldInfo field, Struct val, String locale, String pre, String post) throws OperatingContextException {
		if (vl == null)
			return;
		
		if (field != null) {
			List<IndexInfo> tokens = field.type.toIndexTokens(val, locale);
			
			if (tokens != null) {
				for (IndexInfo info : tokens) {
					vl.add(ByteUtil.buildValue(((pre != null) ? pre : "") + info.token + ((post != null) ? post : "")));
				}
				
				return;
			}
		}

		vl.add(ByteUtil.buildValue(val));
	}
	
	static public List<byte[]> loadIndexValues(RecordStruct pdef, FieldInfo field, String locale) throws OperatingContextException {
		if (pdef == null)
			return null;
		
		return ExpressionUtil.loadIndexValues(pdef.getField("Value"), field, locale);
	}
	
	static public List<byte[]> loadIndexValues(Object val, FieldInfo field, String locale) throws OperatingContextException {
		if (val == null)
			return null;
		
		ArrayList<byte[]> vl = new ArrayList<>();
		
		if (val instanceof ListStruct) {
			ListStruct lval = (ListStruct) val;
			
			for (int i2 = 0; i2 < lval.size(); i2++) {
				ExpressionUtil.loadIndexValue(vl, field, lval.getItem(i2), locale);
			}
		}
		else {
			ExpressionUtil.loadIndexValue(vl, field, val, locale);
		}
		
		return vl;
	}
	
	static public void loadIndexValue(List<byte[]> vl, FieldInfo field, Object val, String locale) throws OperatingContextException {
		if (vl == null)
			return;
		
		if (field != null)
			vl.add(ByteUtil.buildValue(field.type.toIndex(val, locale)));
		else
			vl.add(ByteUtil.buildValue(val));
	}
	
	static public FieldInfo loadField(String table, RecordStruct pdef) throws OperatingContextException {
		if (pdef == null)
			return null;
		
		String pfname = pdef.getFieldAsString("Field");
		
		FieldInfo fieldInfo = ExpressionUtil.loadField(table, pfname);

		if (fieldInfo != null) {
			fieldInfo.subid = pdef.getFieldAsString("SubId");
		}
		
		return fieldInfo;
	}
	
	// TODO add support for Composer
	// TODO add support for Format
	// String pformat = pdef.getFieldAsString("Format");
	// add support for Format, this converts from byte to object, then formats object, then back to byte for compares
	static public FieldInfo loadField(String table, String field) throws OperatingContextException {
		if (StringUtil.isEmpty(table) || StringUtil.isEmpty(field))
			return null;
		
		SchemaResource schema = ResourceHub.getResources().getSchema();
		
		DbField sfld = schema.getDbField(table, field);
		
		if (sfld == null)
			return null;
		
		DataType tfld = schema.getType(sfld.getTypeId());
		
		if (tfld == null)
			return null;
		
		FieldInfo fieldInfo = new FieldInfo();
		fieldInfo.field = sfld;
		fieldInfo.type = tfld;
		
		return fieldInfo;
	}
	
	static public class FieldInfo {
		public DbField field = null;
		public DataType type = null;
		public String subid = null;
	}
}