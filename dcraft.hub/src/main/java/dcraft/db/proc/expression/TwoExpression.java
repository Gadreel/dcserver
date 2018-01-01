package dcraft.db.proc.expression;

import dcraft.db.proc.IExpression;
import dcraft.db.request.schema.Query;
import dcraft.db.tables.TablesAdapter;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
import dcraft.hub.time.BigDateTime;
import dcraft.log.Logger;
import dcraft.struct.RecordStruct;
import dcraft.task.IParentAwareWork;
import dcraft.xml.XElement;

import java.util.List;

abstract public class TwoExpression implements IExpression {
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
		
		// force a raw value, not a search value
		this.values = ExpressionUtil.loadIndexValues(where.getFieldAsRecord("B"), this.fieldInfo, this.lang);
			
		if (this.values == null)
			Logger.error("Contains is missing values");
	}
	
	public int compare(TablesAdapter adapter, String id, BigDateTime when, boolean historical) {
		List<byte[]> data = adapter.getRawIndex(this.table, id, this.fieldInfo.field.getName(), this.fieldInfo.subid, when, historical);
		
		return ExpressionUtil.compare(data, this.values);
	}
	
	@Override
	public void parse(IParentAwareWork state, XElement code, RecordStruct clause) throws OperatingContextException {
		clause.with("A", Query.createWhereField(state, code));
		
		clause.with("B", Query.createWhereValue(state, code, "Value"));
	}
}
