package dcraft.db.proc.expression;

import dcraft.db.tables.TablesAdapter;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.time.BigDateTime;

import java.util.List;

public class Any extends TwoExpression {
	@Override
	public boolean check(TablesAdapter adapter, String id, BigDateTime when, boolean historical) throws OperatingContextException {
		List<byte[]> data = adapter.getRawIndex(this.table, id, this.fieldInfo.field.getName(), this.fieldInfo.subid, when, historical);
		
		return ExpressionUtil.any(data, this.values);
	}
}
