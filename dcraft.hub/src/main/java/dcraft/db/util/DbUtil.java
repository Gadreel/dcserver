package dcraft.db.util;

import dcraft.db.DatabaseException;
import dcraft.db.DbServiceRequest;
import dcraft.db.IConnectionManager;
import dcraft.db.proc.IExpression;
import dcraft.db.proc.IStoredProc;
import dcraft.hub.ResourceHub;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
import dcraft.log.Logger;
import dcraft.log.count.CountHub;
import dcraft.schema.DbProc;
import dcraft.struct.BaseStruct;
import dcraft.struct.RecordStruct;
import dcraft.struct.Struct;

public class DbUtil {
	// TODO add a executeWithValidate that validates the request
	
	// generally call service, not this
	static public boolean execute(DbServiceRequest request, IConnectionManager conn) throws OperatingContextException, DatabaseException {
		BaseStruct params = request.getData();

		if (conn == null) {
			String reqdbname = (params instanceof RecordStruct)
					? ((RecordStruct)params).getFieldAsString("_Database", "default")
					: "default";

			Logger.error("Missing database: " + reqdbname);
			request.getOutcome().returnResult();
			return false;
		}

		DbProc proc = ResourceHub.getResources().getSchema().getDbProc(request.getOp());

		if (proc == null) {
			Logger.error("Missing proc: " + proc);
			request.getOutcome().returnResult();
			return false;
		}

		String spname = proc.execute;		// TODO find class name for request.getOp()

		CountHub.countObjects("dcProcCallCount", request.getOp());
		
		IStoredProc sp = (IStoredProc) ResourceHub.getResources().getClassLoader().getInstance(spname);

		if (sp == null) {
			Logger.error("Unable to load/start procedure class: " + spname);
			request.getOutcome().returnResult();
			return false;
		}
		
		OperationContext.getOrThrow().touch();		// keep us alive

		request
				.withInterface(conn.allocateAdapter())
				.withStamp(conn.allocateStamp(0));

		if ((params instanceof RecordStruct) && ((RecordStruct)params).isFieldEmpty("_ForTenant"))
			((RecordStruct)params).with("_ForTenant", OperationContext.getOrThrow().getUserContext().getTenantAlias());

		// if we are playing a cloned request, then replicating should be set
		//request.withReplicating(true);

		sp.execute(request, request.getOutcome());

		// TODO is audit level is high enough? then audit request
		// - audit req, operation context - including log and error state

		//if (sp instanceof IUpdatingStoredProc)
		// TODO pat of audit level check

		// AUDIT after execute so that additional parameters can be collected for replication

		/*
		 * maybe audit by domain id, user id, stamp instead of task id...which really means little
		 * though in debug mode maybe also index audit by task id in case we need to trace the audit for the task
		 *
		 * STAMP has hid embedded in it so no need for Stamp,hid combo
		 *
		 * replace TaskId with simple entry id? seq value... dbNumber so audit up to 15 digits? more?
		 * then start at 1 again?  assume that audit cleanup will cover old?
		 *
		 ;s ^dcAudit(TaskId,Stamp,hid,"Operation")=$s(Errors+0=0:"Call",1:"FailedCall")
		 ;m ^dcAudit(TaskId,Stamp,hid,"Params")=Params
		 ;s ^dcAudit(TaskId,Stamp,hid,"Execute")=FuncName
		 ;s ^dcAudit(TaskId,Stamp,hid,"UserId")=UserId
		 ;s ^dcAudit(TaskId,Stamp,hid,"NodeId")=NodeId
		 ;
		 ;s ^dcAuditTime(Stamp,hid)=TaskId     ; TODO add user index?
		 ;
		 ;n latestTs
		 ;lock +^dcReplication("Local",hid)
		 ;s latestTs=^dcReplication("Local",hid)
		 ;i Stamp]]latestTs s ^dcReplication("Local",hid)=Stamp
		 ;lock -^dcReplication("Local",hid)
		 ;

		 ^dcReplacation("CompleteStamp")=Stamp   - the latest stamp for which all replications are complete

		*/

		return true;
	}

	// TODO remove after transitioning CoreServices to use proper StoredProcedures
	// do not use unless you have to
	static public DbServiceRequest fakeRequest() {
		return DbUtil.fakeRequest("default");
	}

	static public DbServiceRequest fakeRequest(String dbname) {
		IConnectionManager conn = ResourceHub.getResources().getDatabases().getDatabase(dbname);

		if (conn == null) {
			Logger.error("Missing database: " + dbname);
			return null;
		}

		return DbServiceRequest.of("dcdbFake")
				.withInterface(conn.allocateAdapter())
				.withStamp(conn.allocateStamp(0));
	}
}
