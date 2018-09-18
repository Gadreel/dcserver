package dcraft.db.proc.expression;

import dcraft.db.proc.ExpressionResult;
import dcraft.db.tables.TablesAdapter;
import dcraft.db.util.ByteUtil;
import dcraft.hub.op.IVariableAware;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.time.BigDateTime;
import dcraft.struct.RecordStruct;

import java.util.List;

public class StartsWith extends TwoExpression {
	@Override
	public ExpressionResult check(TablesAdapter adapter, IVariableAware scope, String table, String id) throws OperatingContextException {
		List<byte[]> data = adapter.getRaw(table, id, this.fieldInfo.field.getName(), this.fieldInfo.subid, "Index");
		
		if ((this.values == null) && (data == null))
			return ExpressionResult.ACCEPTED;
		
		// rule out one being null
		if ((this.values == null) || (data == null))
			return ExpressionResult.REJECTED;
		
		for (int i = 0; i < data.size(); i++) {
			for (int i2 = 0; i2 < this.values.size(); i2++) {
				if (ByteUtil.dataStartsWith(data.get(i), this.values.get(i2)))
					return ExpressionResult.ACCEPTED;
			}
		}
		
		return ExpressionResult.REJECTED;
	}
}
