package dcraft.cms.feed.db;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.function.Function;

import dcraft.db.DbServiceRequest;
import dcraft.db.proc.FilterResult;
import dcraft.db.proc.ICollector;
import dcraft.db.proc.IFilter;
import dcraft.db.proc.filter.Max;
import dcraft.db.tables.TablesAdapter;
import dcraft.db.util.ByteUtil;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
import dcraft.hub.time.BigDateTime;
import dcraft.log.Logger;
import dcraft.script.StackUtil;
import dcraft.struct.RecordStruct;
import dcraft.struct.Struct;
import dcraft.task.IParentAwareWork;
import dcraft.xml.XElement;

public class Scan implements ICollector {
	@Override
	public void collect(DbServiceRequest task, TablesAdapter db, String table, BigDateTime when, boolean historical, RecordStruct collector, IFilter filter) throws OperatingContextException {
		RecordStruct extras = collector.getFieldAsRecord("Extras");
		
		// TODO verify fields
		
		String chan = "/" + OperationContext.getOrThrow().getSite().getAlias() + "/" + extras.getFieldAsString("Feed");
		
		Long lasttime = null;

		if (collector.hasField("From")) {
			ZonedDateTime fromdate = collector.getFieldAsDateTime("From");
		
			if (fromdate != null) {
				lasttime = task.getInterface().inverseTime(fromdate);
			}
			else {
				LocalDate tolocal = collector.getFieldAsDate("From");
				
				lasttime = task.getInterface().inverseTime(tolocal.atStartOfDay(ZoneId.of("UTC")));
			}
		}
		
		Long totime = null;
		
		if (collector.hasField("To")) {
			ZonedDateTime todate = collector.getFieldAsDateTime("To");
			
			if (todate != null) {
				totime = task.getInterface().inverseTime(todate);
			}
			else {
				LocalDate tolocal = collector.getFieldAsDate("To");
				
				totime = task.getInterface().inverseTime(tolocal.atStartOfDay(ZoneId.of("UTC")));
			}
		}
		
		boolean reverse = false;  // collector.getFieldAsBooleanOrFalse("Reverse");
		
		if ((lasttime != null) && (totime != null) && (totime.compareTo(lasttime) < 0))
			reverse = true;
		
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
					
					FilterResult filterResult = filter.check(db, lastid, when, historical);
					
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
					
					FilterResult filterResult = filter.check(db, lastid, when, historical);
					
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
				//.with("Reverse", StackUtil.refFromElement(state, code, "Reverse"))
				.with("Max", StackUtil.refFromElement(state, code, "Max"))
				.with("From", StackUtil.refFromElement(state, code, "From"))
				.with("To", StackUtil.refFromElement(state, code, "To"))
				.with("Extras", RecordStruct.record()
						.with("Feed", StackUtil.stringFromElement(state, code, "Feed", "pages"))
						.with("LastId", StackUtil.refFromElement(state, code, "LastId"))
				);
	}
}
