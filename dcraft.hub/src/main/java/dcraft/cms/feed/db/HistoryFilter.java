package dcraft.cms.feed.db;

import dcraft.db.proc.BasicExpression;
import dcraft.db.proc.ExpressionResult;
import dcraft.db.tables.TablesAdapter;
import dcraft.hub.op.IVariableAware;
import dcraft.hub.op.OperatingContextException;
import dcraft.struct.Struct;

public class HistoryFilter extends BasicExpression {
	static public HistoryFilter forDraft() {
		return new HistoryFilter();
	}
	
	@Override
	public ExpressionResult check(TablesAdapter adapter, IVariableAware scope, String table, String id) throws OperatingContextException {
		// these should already be in the file
		if (Struct.objectToBooleanOrFalse(adapter.getStaticScalar(table, id, "dcmCompleted")))
			return ExpressionResult.rejected();
		
		// skip these
		if (Struct.objectToBooleanOrFalse(adapter.getStaticScalar(table, id, "dcmCancelled")))
			return ExpressionResult.rejected();
		
		// only deal with draft mode - later TODO check schedules and apply for version viewed
		if (adapter.getStaticScalar(table, id, "dcmScheduleAt") != null)
			return ExpressionResult.rejected();

		return this.nestOrAccept(adapter, scope, table, id);
	}
}
