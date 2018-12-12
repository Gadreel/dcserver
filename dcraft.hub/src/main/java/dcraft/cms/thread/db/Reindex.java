package dcraft.cms.thread.db;

import dcraft.db.ICallContext;
import dcraft.db.proc.BasicFilter;
import dcraft.db.proc.ExpressionResult;
import dcraft.db.proc.IFilter;
import dcraft.db.proc.IStoredProc;
import dcraft.db.tables.TablesAdapter;
import dcraft.hub.op.IVariableAware;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
import dcraft.hub.op.OperationOutcomeStruct;
import dcraft.log.Logger;
import dcraft.struct.RecordStruct;
import dcraft.struct.Struct;
import dcraft.task.IParentAwareWork;
import dcraft.util.TimeUtil;
import dcraft.xml.XElement;

import java.time.ZonedDateTime;

public class Reindex implements IStoredProc {
	@Override
	public void execute(ICallContext request, OperationOutcomeStruct callback) throws OperatingContextException {
		TablesAdapter db = TablesAdapter.ofNow(request);

		ThreadUtil.clearIndex(db);

		db.traverseRecords(OperationContext.getOrThrow(), "dcmThread", new BasicFilter() {
			@Override
			public ExpressionResult check(TablesAdapter adapter, IVariableAware scope, String table, Object val) throws OperatingContextException {
				ZonedDateTime mod = Struct.objectToDateTime(db.getStaticScalar("dcmThread", val.toString(), "dcmModified"));

				if (mod == null)
					Logger.warn("Missing delivery date: " + mod);
				else
					ThreadUtil.deliver(db, val.toString(), mod, true);

				return ExpressionResult.ACCEPTED;
			}
		});

		callback.returnEmpty();
	}
}
