package dcraft.db.proc.call;

import dcraft.db.ICallContext;
import dcraft.db.proc.IStoredProc;
import dcraft.db.proc.RecordScope;
import dcraft.db.tables.TableUtil;
import dcraft.db.tables.TablesAdapter;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
import dcraft.hub.op.OperationMarker;
import dcraft.hub.op.OperationOutcomeStruct;
import dcraft.log.Logger;
import dcraft.session.SessionHub;
import dcraft.struct.ListStruct;
import dcraft.struct.RecordStruct;
import dcraft.struct.builder.ICompositeBuilder;
import dcraft.struct.builder.ObjectBuilder;
import dcraft.util.StringUtil;

public class StartSession implements IStoredProc {
	@Override
	public void execute(ICallContext request, OperationOutcomeStruct callback) throws OperatingContextException {
		RecordStruct params = request.getDataAsRecord();
		ICompositeBuilder out = new ObjectBuilder();
		String did = request.getTenant();
		TablesAdapter db = TablesAdapter.of(request);
		
		String token = null;
		String uid = params.getFieldAsString("UserId");
		String uname = params.getFieldAsString("Username").trim().toLowerCase();
		
		try (OperationMarker om = OperationMarker.create()) {
			if (request.isReplicating()) {
				token = params.getFieldAsString("Token");
				uid = params.getFieldAsString("Uid");
			}
			else {
				if (StringUtil.isEmpty(uid)) {
					Object userid = db.firstInIndex("dcUser", "dcUsername", uname, false);
					
					if (userid != null) 
						uid = userid.toString();
				}
			}
			
			if (StringUtil.isEmpty(uid)) {
				Logger.errorTr(123);
				callback.returnEmpty();
				return;
			}
			
			if (! db.isCurrent("dcUser", uid)) {
				Logger.errorTr(123);
				callback.returnEmpty();
				return;
			}
			
			if (! request.isReplicating()) {
				token = SessionHub.nextSessionId();
			}
			
			/* TODO specific concern here?
			if (log.hasErrors()) {
				task.complete();
				return;
			}
			*/

			// replication will need these later
			if (! request.isReplicating()) {
				params.with("Token", token);
				params.with("Uid", uid);
			}

			// both isReplicating and normal store the token
			
			request.getInterface().set("root", "dcSession", token, "LastAccess", request.getStamp());
			request.getInterface().set("root", "dcSession", token, "User", uid);
			request.getInterface().set("root", "dcSession", token, "Tenant", did);
			
			// TODO create some way to track last login that doesn't take up db space
			// or make last login an audit thing...track all logins in StaticList?
			
			// done with replication stuff
			if (request.isReplicating()) {
				callback.returnEmpty();
				return;
			}			
			
			// load info about the user
			ListStruct select = ListStruct.list(
					RecordStruct.record()
							.with("Field", "Id")
							.with("Name", "UserId"),
					RecordStruct.record()
							.with("Field", "dcUsername")
							.with("Name", "Username"),
					RecordStruct.record()
						.with("Field", "dcFirstName")
						.with("Name", "FirstName"),
					RecordStruct.record()
						.with("Field", "dcLastName")
						.with("Name", "LastName"),
					RecordStruct.record()
						.with("Field", "dcEmail")
						.with("Name", "Email"),
					RecordStruct.record()
						.with("Field", "dcLocale")
						.with("Name", "Locale"),
					RecordStruct.record()
						.with("Field", "dcChronology")
						.with("Name", "Chronology"),
					// TODO we actually need group tags too - extend how this works
					RecordStruct.record()
						.with("Field", "dcBadges")
						.with("Name", "Badges"),
					RecordStruct.record()
						.with("Value", token)
						.with("Name", "AuthToken")
			);		
			
			TableUtil.writeRecord(out, db, RecordScope.of(OperationContext.getOrThrow()), "dcUser",
					uid, select, true, false);
			
			if (! om.hasErrors()) {
				callback.returnValue(out.toLocal());
				return;
			}
		}
		catch (Exception x) {
			Logger.error("StartSession: Unable to create resp: " + x);
		}
		
		callback.returnEmpty();
	}
}
