package dcraft.cms.thread.db;

import dcraft.db.IRequestContext;
import dcraft.db.proc.ExpressionResult;
import dcraft.db.proc.ICollector;
import dcraft.db.proc.IFilter;
import dcraft.db.proc.filter.CurrentRecord;
import dcraft.db.proc.filter.Max;
import dcraft.db.tables.TablesAdapter;
import dcraft.db.util.ByteUtil;
import dcraft.hub.op.IVariableAware;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
import dcraft.log.Logger;
import dcraft.script.StackUtil;
import dcraft.struct.ListStruct;
import dcraft.struct.RecordStruct;
import dcraft.struct.Struct;
import dcraft.task.IParentAwareWork;
import dcraft.util.TimeUtil;
import dcraft.xml.XElement;
import org.threeten.extra.PeriodDuration;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;

public class Scan implements ICollector {
	@Override
	public void collect(IRequestContext task, TablesAdapter db, IVariableAware scope, String table, RecordStruct collector, IFilter filter) throws OperatingContextException {
		RecordStruct extras = collector.getFieldAsRecord("Extras");

		if (extras == null)
			return;

		ListStruct forlist = extras.getFieldAsList("For");

		if (forlist == null)
			return;

		// TODO verify fields

		for (Struct frs : forlist.items()) {
			RecordStruct frec = Struct.objectToRecord(frs);

			ThreadUtil.traverseThreadIndex(task, db, scope, frec.getFieldAsString("Party"), frec.getFieldAsString("Folder"),
					CurrentRecord.current().withNested(filter));
		}
	}

	@Override
	public RecordStruct parse(IParentAwareWork state, XElement code) throws OperatingContextException {
		ListStruct forwhich = ListStruct.list();

		for (XElement forel : code.selectAll("For")) {
			forwhich.with(RecordStruct.record()
					.with("Folder", StackUtil.stringFromElement(state, forel, "Folder"))
					.with("Party", StackUtil.refFromElement(state, forel, "Party"))
			);
		}

		return RecordStruct.record()
				.with("Func", "dcmScanThread")
				.with("Extras", RecordStruct.record()
						.with("For", forwhich)
				);
	}
}
