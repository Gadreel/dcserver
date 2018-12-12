package dcraft.cms.thread.db;

import dcraft.db.IRequestContext;
import dcraft.db.proc.ExpressionResult;
import dcraft.db.proc.ICollector;
import dcraft.db.proc.IFilter;
import dcraft.db.proc.filter.CurrentRecord;
import dcraft.db.proc.filter.Max;
import dcraft.db.proc.filter.Unique;
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
import dcraft.util.StringUtil;
import dcraft.util.TimeUtil;
import dcraft.xml.XElement;
import org.threeten.extra.PeriodDuration;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

public class Scan implements ICollector {
	@Override
	public void collect(IRequestContext task, TablesAdapter db, IVariableAware scope, String table, RecordStruct collector, IFilter filter) throws OperatingContextException {
		RecordStruct extras = collector.getFieldAsRecord("Extras");

		if (extras == null)
			return;
		
		List<XElement> channeldefs = ThreadUtil.getChannelDefs();

		ListStruct forlist = extras.getFieldAsList("For");

		if ((forlist == null) || (forlist.size() == 0)) {
			if (forlist == null)
				forlist = ListStruct.list();
			
			for (XElement chan : channeldefs) {
				forlist.withItem(RecordStruct.record()
						.with("Channel", chan.attr("Alias"))
				);
			}
		}
		
		filter = Unique.unique().withNested(CurrentRecord.current().withNested(filter));

		for (Struct frs : forlist.items()) {
			RecordStruct frec = Struct.objectToRecord(frs);
			
			String alias = frec.getFieldAsString("Channel");
			String folder = frec.getFieldAsString("Folder", "/InBox");
			
			if (StringUtil.isNotEmpty(alias)) {
				for (XElement chan : channeldefs) {
					if (alias.equals(chan.attr("Alias"))) {
						List<String> parties = ThreadUtil.getChannelAccess(db, scope, chan);
						
						for (String party : parties)
							ThreadUtil.traverseThreadIndex(task, db, scope, party, folder, filter);
						
						break;
					}
				}
			}
		}
	}

	@Override
	public RecordStruct parse(IParentAwareWork state, XElement code) throws OperatingContextException {
		ListStruct forwhich = ListStruct.list();

		String defaultFolder = StackUtil.stringFromElement(state, code, "Folder", "/InBox");
		
		for (XElement forel : code.selectAll("For")) {
			forwhich.with(RecordStruct.record()
					.with("Folder", StackUtil.stringFromElement(state, forel, "Folder", defaultFolder))
					.with("Channel", StackUtil.refFromElement(state, forel, "Channel"))
			);
		}

		return RecordStruct.record()
				.with("Func", "dcmScanThread")
				.with("Extras", RecordStruct.record()
						.with("For", forwhich)
				);
	}
}
