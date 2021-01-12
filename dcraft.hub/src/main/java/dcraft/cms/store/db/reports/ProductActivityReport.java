package dcraft.cms.store.db.reports;

import dcraft.db.ICallContext;
import dcraft.db.proc.BasicFilter;
import dcraft.db.proc.ExpressionResult;
import dcraft.db.proc.IStoredProc;
import dcraft.db.proc.call.Hash;
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

public class ProductActivityReport implements IStoredProc {
	@Override
	public void execute(ICallContext request, OperationOutcomeStruct callback) throws OperatingContextException {
		RecordStruct data = request.getDataAsRecord();

		LocalDate from = data.getFieldAsDate("From");
		LocalDate to = data.getFieldAsDate("To");

		TablesAdapter db = TablesAdapter.ofNow(request);

		Unique orders = Unique.unique();

		db.traverseIndexRange(OperationContext.getOrNull(), "dcmOrder", "dcmOrderDate", from, to, orders.withNested(CurrentRecord.current()));

		Map<String, AtomicInteger> prodordcount = new HashMap<>();
		Map<String, AtomicInteger> prodcount = new HashMap<>();
		Map<String, AtomicReference<BigDecimal>> prodsales = new HashMap<>();

		for (Object ooo : orders.getValues()) {
			String id = ooo.toString();

			List<String> items = db.getStaticListKeys("dcmOrder", id, "dcmItemProduct");

			for (String item : items) {
				String pid = Struct.objectToString(db.getStaticList("dcmOrder", id, "dcmItemProduct", item));
				Long qty = Struct.objectToInteger(db.getStaticList("dcmOrder", id, "dcmItemQuantity", item));
				BigDecimal sales = Struct.objectToDecimal(db.getStaticList("dcmOrder", id, "dcmItemTotal", item));

				if (qty != null) {
					if (prodordcount.containsKey(pid)) {
						prodordcount.get(pid).incrementAndGet();
						prodcount.get(pid).addAndGet(qty.intValue());
						prodsales.get(pid).set(prodsales.get(pid).get().add(sales));
					}
					else {
						prodordcount.put(pid, new AtomicInteger(1));
						prodcount.put(pid, new AtomicInteger(qty.intValue()));
						prodsales.put(pid, new AtomicReference<>(sales));
					}
				}
			}
		}

		orders = null;  // free that memory

		ListStruct result = ListStruct.list();

		for (String pid : prodcount.keySet()) {
			result.with(RecordStruct.record()
					.with("Id", pid)
					.with("Title", db.getStaticScalar("dcmProduct", pid, "dcmTitle"))
					.with("Alias", db.getStaticScalar("dcmProduct", pid, "dcmAlias"))
					.with("Sku", db.getStaticScalar("dcmProduct", pid, "dcmSku"))
					.with("Orders", prodordcount.get(pid).longValue())
					.with("Quantity", prodcount.get(pid).longValue())
					.with("Sales", prodsales.get(pid).get())
			);
		}

		callback.returnValue(result);
	}
}
