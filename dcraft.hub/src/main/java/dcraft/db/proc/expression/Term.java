package dcraft.db.proc.expression;

import dcraft.db.proc.ExpressionResult;
import dcraft.db.proc.IExpression;
import dcraft.db.request.schema.Query;
import dcraft.db.tables.TablesAdapter;
import dcraft.db.util.ByteUtil;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
import dcraft.hub.time.BigDateTime;
import dcraft.log.Logger;
import dcraft.script.StackUtil;
import dcraft.struct.ListStruct;
import dcraft.struct.RecordStruct;
import dcraft.task.IParentAwareWork;
import dcraft.xml.XElement;

import java.util.ArrayList;
import java.util.List;

// term is expected to work on String (searchable) types only
public class Term implements IExpression {
	protected List<ExpressionUtil.FieldInfo> fieldInfo = new ArrayList<>();
	protected List<byte[]> values = null;
	protected String lang = null;
	protected String table = null;
	
	@Override
	public void init(String table, RecordStruct where) throws OperatingContextException {
		this.table = table;
		this.lang = where.getFieldAsString("Locale", OperationContext.getOrThrow().getLocale());
		
		RecordStruct fldrec = where.getFieldAsRecord("A");
		
		if (fldrec == null) {
			Logger.error("Term is missing a field rec");
			return;
		}
		
		ListStruct fldlist = fldrec.getFieldAsList("Value");
		
		if (fldlist == null) {
			Logger.error("Term is missing a field list");
			return;
		}
		
		for (int i = 0; i < fldlist.size(); i++)
			this.fieldInfo.add(ExpressionUtil.loadField(table, fldlist.getItemAsRecord(i)));
		
		if (this.fieldInfo.size() == 0) {
			Logger.error("Term is missing a field");
			return;
		}
		
		for (ExpressionUtil.FieldInfo info : this.fieldInfo) {
			if (! info.type.isSearchable()) {
				Logger.error("Term include a non-searchable field");
				return;
			}
		}
		
		this.values = ExpressionUtil.loadSearchValues(where.getFieldAsRecord("B"), this.fieldInfo.get(0), this.lang, "|", null);
		
		if (this.values == null) {
			Logger.error("Term is missing values");
			return;
		}
	}
	
	@Override
	public ExpressionResult check(TablesAdapter adapter, String id) throws OperatingContextException {
		// TODO enhance to use scoring, to require all token
		boolean[] found = new boolean[this.values.size()];
		
		for (ExpressionUtil.FieldInfo info : this.fieldInfo) {
			List<byte[]> data = adapter.getRaw(table, id, info.field.getName(), info.subid, "Search");
			
			if ((this.values == null) && (data == null))
				return ExpressionResult.ACCEPTED;
			
			// rule out one being null
			if ((this.values == null) || (data == null))
				return ExpressionResult.REJECTED;
			
			for (int i = 0; i < data.size(); i++) {
				for (int i2 = 0; i2 < this.values.size(); i2++) {
					if (ByteUtil.dataContains(data.get(i), this.values.get(i2)))
						found[i2] = true;
				}
			}
		}
		
		for (boolean b : found) {
			if (! b)
				return ExpressionResult.REJECTED;
		}
		
		return ExpressionResult.ACCEPTED;
	}
	
	@Override
	public void parse(IParentAwareWork state, XElement code, RecordStruct clause) throws OperatingContextException {
		String[] fields = StackUtil.stringFromElement(state, code, "Fields", "").split(",");
		ListStruct fvalue = ListStruct.list();
		
		for (String fld : fields) {
			int pos = fld.indexOf(":");
			
			if (pos > -1) {
				fvalue.with(RecordStruct.record()
						.with("Field", fld.substring(0, pos))
						.with("SubId", fld.substring(pos + 1))
				);
			}
			else {
				fvalue.with(RecordStruct.record().with("Field", fld));
			}
		}
		
		clause.with("A", RecordStruct.record().with("Value", fvalue));
		clause.with("B", Query.createWhereValue(state, code, "Value"));
	}
}
