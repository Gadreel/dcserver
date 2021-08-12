package dcraft.cms.store.db.reports;

import dcraft.db.ICallContext;
import dcraft.db.proc.BasicFilter;
import dcraft.db.proc.ExpressionResult;
import dcraft.db.proc.IStoredProc;
import dcraft.db.proc.filter.CurrentRecord;
import dcraft.db.proc.filter.Unique;
import dcraft.db.tables.TablesAdapter;
import dcraft.hub.op.IVariableAware;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
import dcraft.hub.op.OperationOutcomeStruct;
import dcraft.struct.ListStruct;
import dcraft.struct.RecordStruct;
import dcraft.struct.Struct;
import dcraft.util.TimeUtil;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public class ProductChangesReport implements IStoredProc {
	@Override
	public void execute(ICallContext request, OperationOutcomeStruct callback) throws OperatingContextException {
		RecordStruct data = request.getDataAsRecord();

		ZonedDateTime from = TimeUtil.getStartOfDayInContext(data.getFieldAsDate("From"));
		ZonedDateTime to = TimeUtil.getStartOfDayInContext(data.getFieldAsDate("To"));

		BigDecimal fromstamp = BigDecimal.valueOf(request.getInterface().inverseTime(from));
		BigDecimal tostamp = BigDecimal.valueOf(request.getInterface().inverseTime(to));

		TablesAdapter db = TablesAdapter.of(request);

		ListStruct result = ListStruct.list();

		db.traverseRecords(OperationContext.getOrNull(), "dcmProduct", CurrentRecord.current().withNested(new BasicFilter() {
			@Override
			public ExpressionResult check(TablesAdapter adapter, IVariableAware scope, String table, Object val) throws OperatingContextException {
				String id = val.toString();

				ListStruct changes = ListStruct.list();

				// check
				checkScalarField(db, table, id, "dcmTitle", fromstamp, tostamp, changes);
				checkListField(db, table, id, "dcmTitleTr", fromstamp, tostamp, changes);

				checkScalarField(db, table, id, "dcmDescription", fromstamp, tostamp, changes);
				checkListField(db, table, id, "dcmDescriptionTr", fromstamp, tostamp, changes);

				checkScalarField(db, table, id, "dcmPrice", fromstamp, tostamp, changes);

				// report any
				if (changes.size() > 0) {
					result.with(RecordStruct.record()
							.with("Id", id)
							.with("Title", db.getScalar("dcmProduct", id, "dcmTitle"))
							.with("Sku", db.getScalar("dcmProduct", id, "dcmSku"))
							.with("Changes", changes)
					);
				}

				return ExpressionResult.ACCEPTED;
			}
		}));

		result.sortRecords("Title",false);

		callback.returnValue(result);
	}

	protected void checkScalarField(TablesAdapter db, String table, String id, String field, BigDecimal fromstamp, BigDecimal tostamp, ListStruct changes) throws OperatingContextException {
		BigDecimal fndstamp = db.getScalarNextStamp(table, id, field, tostamp);

		if (checkStampAfter(fndstamp, fromstamp) && (db.getRaw(table, id, field, fndstamp) != null))
			changes.with(RecordStruct.record()
					.with("Field", fieldname(field))
					.with("At", db.getRequest().getInterface().restoreTime(fndstamp.longValue()))
					.with("Locale", OperationContext.getOrThrow().getTenant().getResources().getLocale().getDefaultLocale())
					.with("Value", db.getScalar(table, id, field, fndstamp, null))
			);
	}

	protected void checkListField(TablesAdapter db, String table, String id, String field, BigDecimal fromstamp, BigDecimal tostamp, ListStruct changes) throws OperatingContextException {
		for (String key : db.getListKeys(table, id, field)) {
			BigDecimal fndstamp = db.getListNextStamp(table, id, field, key, tostamp);

			if (checkStampAfter(fndstamp, fromstamp) && (db.getListRaw(table, id, field, key, fndstamp) != null))
				changes.with(RecordStruct.record()
						.with("Field", fieldname(field))
						.with("At", db.getRequest().getInterface().restoreTime(fndstamp.longValue()))
						.with("Locale", key)
						.with("Value", db.getList(table, id, field, key, fndstamp, null))
				);
		}
	}

	protected String fieldname(String field) {
		for (int i = 0; i < field.length(); i++) {
			if (Character.isUpperCase(field.charAt(i)))
				return field.substring(i);
		}

		return field;
	}

	protected boolean checkStampAfter(BigDecimal stamp, BigDecimal from) {
		return ((stamp != null) && (stamp.abs().compareTo(from.abs()) >= 0));
	}
}
