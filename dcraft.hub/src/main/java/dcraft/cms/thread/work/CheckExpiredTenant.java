package dcraft.cms.thread.work;

import dcraft.cms.thread.db.ThreadUtil;
import dcraft.db.BasicRequestContext;
import dcraft.db.DatabaseAdapter;
import dcraft.db.IRequestContext;
import dcraft.db.proc.filter.CurrentRecord;
import dcraft.db.proc.filter.Unique;
import dcraft.db.tables.TablesAdapter;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
import dcraft.log.Logger;
import dcraft.struct.Struct;
import dcraft.task.IWork;
import dcraft.task.TaskContext;
import dcraft.util.TimeUtil;

import java.time.ZonedDateTime;

public class CheckExpiredTenant implements IWork {
	static public CheckExpiredTenant of(DatabaseAdapter conn) {
		CheckExpiredTenant tenant = new CheckExpiredTenant();
		tenant.conn = conn;
		return tenant;
	}
	
	protected DatabaseAdapter conn = null;
	
	@Override
	public void run(TaskContext taskctx) throws OperatingContextException {
		IRequestContext tablesContext = BasicRequestContext.of(this.conn);
		TablesAdapter db = TablesAdapter.ofNow(tablesContext);
		ZonedDateTime now = TimeUtil.now();

		Logger.info("Checking expired threads for: " + OperationContext.getOrThrow().getTenant().getAlias());

		Unique collector = Unique.unique();

		db.traverseIndexRange(OperationContext.getOrThrow(), "dcmThread", "dcmExpireDate", null, TimeUtil.now(), CurrentRecord.current().withNested(collector));

		for (Object val : collector.getValues()) {
			String tid = val.toString();

			Logger.info("Checking expired: " + tid + " - " + Struct.objectToString(db.getStaticScalar("dcmThread", tid, "dcmExpireDate")));

			if (db.executeCanTrigger("dcmThread", tid,"CanExpireDeleteCheck", null)) {
				db.deleteRecord("dcmThread", tid);
			}
			else if (db.executeCanTrigger("dcmThread", tid,"CanExpireRetireCheck", null)) {
				ThreadUtil.retireThread(db, tid);

				db.retireStaticScalar("dcmThread", tid, "dcmExpireDate");
				db.updateStaticScalar("dcmThread", tid, "dcmExpiredDate", now);
			}
		}

		taskctx.returnEmpty();
	}
}
