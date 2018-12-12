package dcraft.db.proc.expression;

import dcraft.db.proc.ExpressionResult;
import dcraft.db.request.schema.Query;
import dcraft.db.tables.TablesAdapter;
import dcraft.hub.op.IVariableAware;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.time.BigDateTime;
import dcraft.log.Logger;
import dcraft.struct.RecordStruct;
import dcraft.task.IParentAwareWork;
import dcraft.xml.XElement;

import java.util.List;

public class Range extends TwoExpression {
	protected List<byte[]> values2 = null;
	
	@Override
	public void init(String table, RecordStruct where) throws OperatingContextException {
		super.init(table, where);
		
		this.values2 = ExpressionUtil.loadIndexValues(where.getFieldAsRecord("C"), this.fieldInfo, this.lang);
		
		if (this.values2 == null)
			Logger.error("Range is missing values");
	}
	
	@Override
	public ExpressionResult check(TablesAdapter adapter, IVariableAware scope, String table, String id) throws OperatingContextException {
		List<byte[]> data = adapter.getRaw(table, id, this.fieldInfo.field.getName(), this.fieldInfo.subid, "Index");
		
		if (ExpressionUtil.compare(data, this.values) > -1) {
			if (ExpressionUtil.compare(data, this.values2) < 1) {
				return this.nestOrAccept(adapter, scope, table, id);
			}
		}
		
		return ExpressionResult.REJECTED;
	}
	
	@Override
	public void parse(IParentAwareWork state, XElement code, RecordStruct clause) throws OperatingContextException {
		clause.with("A", Query.createWhereField(state, code));
		
		clause.with("B", Query.createWhereValue(state, code, "From"));
		clause.with("C", Query.createWhereValue(state, code, "To"));
	}
}
