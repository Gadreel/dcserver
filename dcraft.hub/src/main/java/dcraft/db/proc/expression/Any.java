package dcraft.db.proc.expression;

import dcraft.db.proc.ExpressionResult;
import dcraft.db.tables.TablesAdapter;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.time.BigDateTime;

import java.util.List;

public class Any extends TwoExpression {
	@Override
	public ExpressionResult check(TablesAdapter adapter, String id) throws OperatingContextException {
		List<byte[]> data = adapter.getRaw(this.table, id, this.fieldInfo.field.getName(), this.fieldInfo.subid, "Index");
		
		return ExpressionUtil.any(data, this.values) ? ExpressionResult.ACCEPTED : ExpressionResult.REJECTED;
	}
}
