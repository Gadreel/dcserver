package dcraft.db.proc.expression;

import dcraft.db.Constants;
import dcraft.db.proc.ExpressionResult;
import dcraft.db.tables.TablesAdapter;
import dcraft.db.util.ByteUtil;
import dcraft.hub.op.IVariableAware;
import dcraft.hub.op.OperatingContextException;

import java.util.List;

public class IsEmpty extends OneExpression {
	@Override
	public ExpressionResult check(TablesAdapter adapter, IVariableAware scope, String table, String id) throws OperatingContextException {
		List<byte[]> data = adapter.getRaw(table, id, this.fieldInfo.field.getName(), this.fieldInfo.subid, "Data");
		
		if (data == null)
			return this.nestOrAccept(adapter, scope, table, id);
		
		for (int i = 0; i < data.size(); i++) {
			byte[] d = data.get(i);
			
			if (d == null)
				continue;
			
			if (ByteUtil.compareKeys(d, Constants.DB_EMPTY_ARRAY) != 0)
				return ExpressionResult.REJECTED;
		}
		
		return this.nestOrAccept(adapter, scope, table, id);
	}
}
