package dcraft.core.db.user;

import dcraft.db.ICallContext;
import dcraft.db.proc.IStoredProc;
import dcraft.db.proc.filter.CurrentRecord;
import dcraft.db.proc.filter.Unique;
import dcraft.db.request.query.SelectFields;
import dcraft.db.request.query.WhereAnd;
import dcraft.db.request.query.WhereAny;
import dcraft.db.request.query.WhereNot;
import dcraft.db.request.query.WhereTerm;
import dcraft.db.tables.TableUtil;
import dcraft.db.tables.TablesAdapter;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
import dcraft.hub.op.OperationOutcomeStruct;
import dcraft.struct.ListStruct;
import dcraft.struct.RecordStruct;
import dcraft.struct.Struct;
import dcraft.util.StringUtil;

import java.util.Collection;

public class Search implements IStoredProc {
	@Override
	public void execute(ICallContext request, OperationOutcomeStruct callback) throws OperatingContextException {
		RecordStruct data = request.getDataAsRecord();
		
		TablesAdapter adapter = TablesAdapter.ofNow(request);
		
		Unique collectIds = Unique.unique();
		
		// term
		
		String term = data.getFieldAsString("Term");
		
		if (StringUtil.isNotEmpty(term)) {
			WhereAnd whereAnd = WhereAnd.of();
			
			whereAnd.withExpression(WhereTerm.term()
					.withFields("dcUsername", "dcFirstName", "dcLastName", "dcEmail", "dcAddress", "dcCity", "dcState", "dcZip", "dcPhone")
					.withValueTwo(term)
			);
			
			collectIds.withNested(whereAnd.toFilter("dcUser"));
		}

		ListStruct badges = data.getFieldAsList("Badges");
		
		if ((badges != null) && (badges.size() > 0)) {
			for (Struct bs : badges.items()) {
				String badge = Struct.objectToString(bs);
				
				adapter.traverseIndex(OperationContext.getOrThrow(),"dcUser", "dcBadges", badge, badge, CurrentRecord.current().withNested(collectIds));
			}
		}
		else {
			adapter.traverseRecords(OperationContext.getOrThrow(),"dcUser", CurrentRecord.current().withNested(collectIds));
		}
		
		Collection<Object> values = collectIds.getValues();
		
		// do search
		ListStruct finres = new ListStruct();
		
		SelectFields selectFields = SelectFields.select()
				.with("Id")
				.with("dcLocation", "Location")
				.with("dcUsername", "Username")
				.with("dcFirstName", "FirstName")
				.with("dcLastName", "LastName")
				.with("dcEmail", "Email")
				.with("dcBadges", "Badges")
				.with("dcAddress", "Address")
				.with("dcCity", "City")
				.with("dcState", "State")
				.with("dcZip", "Zip")
				.with("dcPhone", "Phone");
		
		for (Object oid : values) {
			finres.with(TableUtil.getRecord(adapter, OperationContext.getOrThrow(), "dcUser", oid.toString(), selectFields));
		}
		
		callback.returnValue(finres);
	}
}

