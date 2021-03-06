package dcraft.db.proc.expression;

import dcraft.db.proc.ExpressionResult;
import dcraft.db.request.query.WhereLessThan;
import dcraft.db.tables.TablesAdapter;
import dcraft.hub.op.IVariableAware;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.time.BigDateTime;

public class LessThan extends TwoExpression {
		/*
	static public LessThan of(String table, WhereLessThan expression) throws OperatingContextException {
		LessThan obj = new LessThan();
		
		obj.init(table, expression.getParams());
		
		obj.table = table;
		obj.fieldInfo = ExpressionUtil.loadField(table, field);
		obj.lang = lang;
		obj.values = ExpressionUtil.loadIndexValues(val, obj.fieldInfo, lang);
		
		return obj;
	}
		*/
	
	@Override
	public ExpressionResult check(TablesAdapter adapter, IVariableAware scope, String table, String id) throws OperatingContextException {
		return (this.compare(adapter, id) == -1) ? this.nestOrAccept(adapter, scope, table, id) : ExpressionResult.REJECTED;
	}
}
