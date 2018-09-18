package dcraft.cms.feed.db;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import dcraft.db.IRequestContext;
import dcraft.db.proc.ExpressionResult;
import dcraft.db.proc.ICollector;
import dcraft.db.proc.IFilter;
import dcraft.db.proc.filter.Max;
import dcraft.db.ICallContext;
import dcraft.db.tables.TablesAdapter;
import dcraft.db.util.ByteUtil;
import dcraft.hub.op.IVariableAware;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
import dcraft.hub.time.BigDateTime;
import dcraft.log.Logger;
import dcraft.script.StackUtil;
import dcraft.struct.RecordStruct;
import dcraft.struct.Struct;
import dcraft.task.IParentAwareWork;
import dcraft.util.TimeUtil;
import dcraft.xml.XElement;
import org.threeten.extra.PeriodDuration;

public class Scan implements ICollector {
	@Override
	public void collect(IRequestContext task, TablesAdapter db, IVariableAware scope, String table, RecordStruct collector, IFilter filter) throws OperatingContextException {
		RecordStruct extras = collector.getFieldAsRecord("Extras");
		
		// TODO verify fields
		
		String chan = "/" + OperationContext.getOrThrow().getSite().getAlias() + "/" + extras.getFieldAsString("Feed");

		// FROM

		Long lasttime = null;

		if (collector.isNotFieldEmpty("From")) {
			String frominfo = collector.getFieldAsString("From");

			ZonedDateTime fromdate = null;

			if ("-".equals(frominfo))
				fromdate = ZonedDateTime.of(1, 1, 1, 0, 0, 0, 0, ZoneId.of("UTC"));
			else if ("+".equals(frominfo))
				fromdate = ZonedDateTime.of(3000, 1, 1, 0, 0, 0, 0, ZoneId.of("UTC"));
			else if ("now".equals(frominfo))
				fromdate = TimeUtil.now();

			if (fromdate == null) {
				fromdate = collector.getFieldAsDateTime("From");
			}

			if (fromdate == null) {
				LocalDate tolocal = collector.getFieldAsDate("From");

				if (tolocal != null)
					fromdate = tolocal.atStartOfDay(ZoneId.of("UTC"));
			}

			if (fromdate == null) {
				PeriodDuration period = PeriodDuration.parse(frominfo);

				if (period != null)
					fromdate = TimeUtil.now().plus(period);
			}

			if (fromdate != null)
				lasttime = task.getInterface().inverseTime(fromdate);
		}

		if (lasttime == null)
			lasttime = task.getInterface().inverseTime(ZonedDateTime.of(1000, 1, 1, 0, 0, 0, 0, ZoneId.of("UTC")));

		// TO

		Long totime = null;
		
		if (collector.isNotFieldEmpty("To")) {
			String toinfo = collector.getFieldAsString("To");

			ZonedDateTime todate = null;

			if ("-".equals(toinfo))
				todate = ZonedDateTime.of(1, 1, 1, 0, 0, 0, 0, ZoneId.of("UTC"));
			else if ("+".equals(toinfo))
				todate = ZonedDateTime.of(3000, 1, 1, 0, 0, 0, 0, ZoneId.of("UTC"));
			else if ("now".equals(toinfo))
				todate = TimeUtil.now();

			if (todate == null) {
				todate = collector.getFieldAsDateTime("To");
			}

			if (todate == null) {
				LocalDate tolocal = collector.getFieldAsDate("To");

				if (tolocal != null)
					todate = tolocal.atStartOfDay(ZoneId.of("UTC"));
			}

			if (todate == null) {
				PeriodDuration period = PeriodDuration.parse(toinfo);

				if (period != null)
					todate = TimeUtil.now().plus(period);
			}

			if (todate != null)
				totime = task.getInterface().inverseTime(todate);
		}

		if (totime == null) {
			if (extras.getFieldAsBooleanOrFalse("Reverse"))
				totime = task.getInterface().inverseTime(ZonedDateTime.of(1900, 1, 1, 0, 0, 0, 0, ZoneId.of("UTC")));
			else
				totime = task.getInterface().inverseTime(ZonedDateTime.of(3000, 1, 1, 0, 0, 0, 0, ZoneId.of("UTC")));
		}
		
		boolean reverse = (totime.compareTo(lasttime) < 0);
		
		Object lastid = extras.getFieldAsString("LastId");
		
		if (collector.hasField("Max"))
			filter = Max.max().withMax(collector.getFieldAsInteger("Max", 100)).withNested(filter);
		
		// TODO add Tags filter

		/*
		 * ^dcmFeedIndex(did, channel, publish datetime, id)=[content tags]
		 */
		
		String did = task.getTenant();
		
		try {
			if (reverse) {
				if (lasttime == null) 
					lasttime = Struct.objectToInteger(ByteUtil.extractValue(task.getInterface().prevPeerKey(did, "dcmFeedIndex", chan, null)));
				
				while ((lasttime != null) && ((totime == null) || (totime.compareTo(((Number) lasttime).longValue()) < 0))) {
					lastid = ByteUtil.extractValue(task.getInterface().prevPeerKey(did, "dcmFeedIndex", chan, lasttime, lastid));		// might return null
	
					// try the next publish time
					if (lastid == null) {
						lasttime = Struct.objectToInteger(ByteUtil.extractValue(task.getInterface().prevPeerKey(did, "dcmFeedIndex", chan, lasttime)));
						
						continue;
					}
					
					ExpressionResult filterResult = filter.check(db, scope,"dcmFeed", lastid);
					
					if (! filterResult.resume)
						break;
				}
			}
			else {
				if (lasttime == null)
					lasttime = Struct.objectToInteger(ByteUtil.extractValue(task.getInterface().nextPeerKey(did, "dcmFeedIndex", chan, null)));
				
				while ((lasttime != null) && ((totime == null) || (totime.compareTo(((Number) lasttime).longValue()) > 0))) {
					lastid = ByteUtil.extractValue(task.getInterface().nextPeerKey(did, "dcmFeedIndex", chan, lasttime, lastid));		// might return null
	
					// try the next publish time
					if (lastid == null) {
						lasttime = Struct.objectToInteger(ByteUtil.extractValue(task.getInterface().nextPeerKey(did, "dcmFeedIndex", chan, lasttime)));
						
						continue;
					}
					
					ExpressionResult filterResult = filter.check(db, scope,"dcmFeed", lastid);
					
					if (! filterResult.resume)
						break;
				}
			}
		}
		catch (Exception x) {
			Logger.error("Error scanning feed index: " + x);
		}
	}

	@Override
	public RecordStruct parse(IParentAwareWork state, XElement code) throws OperatingContextException {
		return RecordStruct.record()
				.with("Func", "dcmScanFeed")
				.with("Max", StackUtil.refFromElement(state, code, "Max", true))
				.with("From", StackUtil.refFromElement(state, code, "From", true))
				.with("To", StackUtil.refFromElement(state, code, "To", true))
				.with("Extras", RecordStruct.record()
						.with("Reverse", StackUtil.refFromElement(state, code, "Reverse", true))
						.with("Feed", StackUtil.stringFromElement(state, code, "Feed", "pages"))
						.with("LastId", StackUtil.refFromElement(state, code, "LastId", true))
				);
	}
}
