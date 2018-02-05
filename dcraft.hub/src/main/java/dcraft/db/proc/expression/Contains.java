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
import dcraft.struct.RecordStruct;
import dcraft.task.IParentAwareWork;
import dcraft.xml.XElement;

import java.util.List;

public class Contains implements IExpression {
	protected ExpressionUtil.FieldInfo fieldInfo = null;
	protected List<byte[]> values = null;
	protected String lang = null;
	protected String table = null;
	
	@Override
	public void init(String table, RecordStruct where) throws OperatingContextException {
		this.table = table;
		this.lang = where.getFieldAsString("Locale", OperationContext.getOrThrow().getLocale());
		
		this.fieldInfo = ExpressionUtil.loadField(table, where.getFieldAsRecord("A"));
		
		if (this.fieldInfo == null) {
			Logger.error("Contains is missing a field");
			return;
		}
		
		if (this.fieldInfo.type.isSearchable())
			this.values = ExpressionUtil.loadSearchValues(where.getFieldAsRecord("B"), this.fieldInfo, this.lang, "|", null);
		else
			this.values = ExpressionUtil.loadSearchValues(where.getFieldAsRecord("B"), null, this.lang, null, null);
		
		if (this.values == null)
			Logger.error("Contains is missing values");
	}
	
	@Override
	public ExpressionResult check(TablesAdapter adapter, String id) throws OperatingContextException {
		List<byte[]> data = adapter.getRaw(table, id, this.fieldInfo.field.getName(), this.fieldInfo.subid, "Search");
		
		if ((this.values == null) && (data == null))
			return ExpressionResult.ACCEPTED;
		
		// rule out one being null
		if ((this.values == null) || (data == null))
			return ExpressionResult.REJECTED;
		
		for (int i = 0; i < data.size(); i++) {
			for (int i2 = 0; i2 < this.values.size(); i2++) {
				if (ByteUtil.dataContains(data.get(i), this.values.get(i2)))
					return ExpressionResult.ACCEPTED;
			}
		}
		
		return ExpressionResult.REJECTED;
	}
	
	@Override
	public void parse(IParentAwareWork state, XElement code, RecordStruct clause) throws OperatingContextException {
		clause.with("A", Query.createWhereField(state, code));
		
		clause.with("B", Query.createWhereValue(state, code, "Value"));
	}
}
