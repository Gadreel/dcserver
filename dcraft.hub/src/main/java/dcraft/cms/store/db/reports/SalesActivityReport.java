package dcraft.cms.store.db.reports;

import dcraft.db.ICallContext;
import dcraft.db.proc.IStoredProc;
import dcraft.db.proc.filter.CurrentRecord;
import dcraft.db.proc.filter.Unique;
import dcraft.db.tables.TablesAdapter;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
import dcraft.hub.op.OperationOutcomeStruct;
import dcraft.struct.ListStruct;
import dcraft.struct.RecordStruct;
import dcraft.struct.Struct;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public class SalesActivityReport implements IStoredProc {
	@Override
	public void execute(ICallContext request, OperationOutcomeStruct callback) throws OperatingContextException {
		RecordStruct data = request.getDataAsRecord();

		String year = data.getFieldAsString("Year");

		LocalDate from = LocalDate.of(Integer.parseInt(year), 1, 1);
		LocalDate to = from.plusYears(1);

		TablesAdapter db = TablesAdapter.of(request);

		Unique orders = Unique.unique();

		db.traverseIndexRange(OperationContext.getOrNull(), "dcmOrder", "dcmOrderDate", from, to, orders.withNested(CurrentRecord.current()));

		Map<Integer, AtomicInteger> monthordcount = new HashMap<>();
		Map<Integer, AtomicReference<BigDecimal>> monthsales = new HashMap<>();
		Map<Integer, AtomicReference<BigDecimal>> monthship = new HashMap<>();
		Map<Integer, AtomicReference<BigDecimal>> monthtax = new HashMap<>();
		Map<Integer, AtomicReference<BigDecimal>> monthtotal = new HashMap<>();

		for (Object ooo : orders.getValues()) {
			String id = ooo.toString();

			ZonedDateTime orddate = Struct.objectToDateTime(db.getScalar("dcmOrder", id, "dcmOrderDate"));

			int month = orddate.getMonth().getValue();

			RecordStruct calcinfo = Struct.objectToRecord(db.getScalar("dcmOrder", id, "dcmCalcInfo"));

			if (monthordcount.containsKey(month)) {
				monthordcount.get(month).incrementAndGet();
				monthsales.get(month).set(monthsales.get(month).get().add(calcinfo.getFieldAsDecimal("ItemTotal")));
				monthship.get(month).set(monthship.get(month).get().add(calcinfo.getFieldAsDecimal("ShipTotal")));
				monthtax.get(month).set(monthtax.get(month).get().add(calcinfo.getFieldAsDecimal("TaxTotal")));
				monthtotal.get(month).set(monthtotal.get(month).get().add(calcinfo.getFieldAsDecimal("GrandTotal")));
			}
			else {
				monthordcount.put(month, new AtomicInteger(1));
				monthsales.put(month, new AtomicReference<>(calcinfo.getFieldAsDecimal("ItemTotal")));
				monthship.put(month, new AtomicReference<>(calcinfo.getFieldAsDecimal("ShipTotal")));
				monthtax.put(month, new AtomicReference<>(calcinfo.getFieldAsDecimal("TaxTotal")));
				monthtotal.put(month, new AtomicReference<>(calcinfo.getFieldAsDecimal("GrandTotal")));
			}
		}

		orders = null;  // free that memory

		ListStruct result = ListStruct.list();

		for (int i = 1; i < 13; i++) {
			result.with(RecordStruct.record()
					.with("Month", i)
					.with("Count", monthordcount.containsKey(i) ? monthordcount.get(i).get() : 0L)
					.with("ItemTotal", monthsales.containsKey(i) ? monthsales.get(i).get() : BigDecimal.ZERO)
					.with("ShipTotal", monthship.containsKey(i) ? monthship.get(i).get() : BigDecimal.ZERO)
					.with("TaxTotal", monthtax.containsKey(i) ? monthtax.get(i).get() : BigDecimal.ZERO)
					.with("Total", monthtotal.containsKey(i) ? monthtotal.get(i).get() : BigDecimal.ZERO)
			);
		}

		callback.returnValue(result);
	}
}
