package dcraft.db.proc.expression;

import dcraft.db.Constants;
import dcraft.db.proc.ExpressionResult;
import dcraft.db.proc.IFilter;
import dcraft.db.request.query.WhereIs;
import dcraft.db.tables.TablesAdapter;
import dcraft.db.util.ByteUtil;
import dcraft.hub.op.IVariableAware;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.time.BigDateTime;

import java.util.List;

public class Is extends OneExpression {
		/*
	static public Is of(String table, WhereIs expression) throws OperatingContextException {
		Is obj = new Is();
		
		obj.init(table, expression.getParams());
		
		obj.table = table;
		obj.fieldInfo = ExpressionUtil.loadField(table, field);
		obj.lang = lang;
		
		return obj;
	}
		*/

	@Override
	public ExpressionResult check(TablesAdapter adapter, IVariableAware scope, String table, String id) throws OperatingContextException {
		List<byte[]> data = adapter.getRaw(table, id, this.fieldInfo.field.getName(), this.fieldInfo.subid, "Data");
		
		if (data == null)
			return ExpressionResult.REJECTED;
		
		for (int i = 0; i < data.size(); i++) {
			byte[] d = data.get(i);
			
			if (d == null)
				continue;
			
			if (ByteUtil.compareKeys(d, Constants.DB_EMPTY_ARRAY) != 0)
				return this.nestOrAccept(adapter, scope, table, id);
		}
		
		return ExpressionResult.REJECTED;
	}
}
