package dcraft.cms.thread.db;

import dcraft.db.ICallContext;
import dcraft.db.proc.BasicFilter;
import dcraft.db.proc.ExpressionResult;
import dcraft.db.proc.IStoredProc;
import dcraft.db.proc.filter.CurrentRecord;
import dcraft.db.tables.TablesAdapter;
import dcraft.hub.op.IVariableAware;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
import dcraft.hub.op.OperationOutcomeStruct;
import dcraft.log.Logger;
import dcraft.struct.Struct;

import java.time.ZonedDateTime;

public class Reindex implements IStoredProc {
	@Override
	public void execute(ICallContext request, OperationOutcomeStruct callback) throws OperatingContextException {
		TablesAdapter db = TablesAdapter.of(request);

		ThreadUtil.clearIndex(db);

		db.traverseRecords(OperationContext.getOrThrow(), "dcmThread", CurrentRecord.current().withNested(new BasicFilter() {
			@Override
			public ExpressionResult check(TablesAdapter adapter, IVariableAware scope, String table, Object val) throws OperatingContextException {
				ZonedDateTime mod = Struct.objectToDateTime(db.getScalar("dcmThread", val.toString(), "dcmModified"));

				if (mod == null)
					Logger.warn("Missing delivery date: " + mod);
				else
					ThreadUtil.deliver(db, val.toString(), mod, true);

				return ExpressionResult.ACCEPTED;
			}
		}));

		callback.returnEmpty();
	}
}
