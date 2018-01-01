package dcraft.cms.feed.proc;

import java.time.ZonedDateTime;
import java.util.function.Function;

import dcraft.db.DbServiceRequest;
import dcraft.db.proc.ICollector;
import dcraft.db.util.ByteUtil;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
import dcraft.log.Logger;
import dcraft.struct.RecordStruct;

public class FeedScan implements ICollector {
	@Override
	public void collect(DbServiceRequest task, RecordStruct collector, Function<Object, Boolean> uniqueConsumer) throws OperatingContextException {
		RecordStruct extras = collector.getFieldAsRecord("Extras");
		
		// TODO verify fields
		
		String chan = "/" + OperationContext.getOrThrow().getSite().getAlias() + "/" + extras.getFieldAsString("Channel");
		ZonedDateTime fromdate = extras.getFieldAsDateTime("FromDate");
		Object lasttime = null;
		
		if (fromdate != null)
			lasttime = task.getInterface().inverseTime(fromdate);
		
		ZonedDateTime todate = extras.getFieldAsDateTime("ToDate");
		Long totime = null;
		
		if (todate != null)
			totime = task.getInterface().inverseTime(todate);
		
		boolean reverse = extras.getFieldAsBooleanOrFalse("Reverse");
		
		if ((fromdate != null) && (todate != null) && (todate.isBefore(fromdate)))
			reverse = true;
		
		Object lastid = extras.getFieldAsString("LastId");
		long max = extras.getFieldAsInteger("Max", 100);
		long cnt = 0;
		
		// TODO add Tags filter
		
		// TODO how to do the Preview index
		
		// TODO add site

		/*
		 * ^dcmFeedIndex(did, channel, publish datetime, id)=[content tags]
		 */
		
		String did = task.getTenant();
		
		try {
			if (reverse) {
				if (lasttime == null) 
					lasttime = ByteUtil.extractValue(task.getInterface().prevPeerKey(did, "dcmFeedIndex", chan, null));
				
				while ((cnt < max) && (lasttime != null) && ((totime == null) || (totime.compareTo(((Number) lasttime).longValue()) < 0))) {
					lastid = ByteUtil.extractValue(task.getInterface().nextPeerKey(did, "dcmFeedIndex", chan, lasttime, lastid));		// might return null
	
					// try the next publish time
					if (lastid == null) {
						lasttime = ByteUtil.extractValue(task.getInterface().prevPeerKey(did, "dcmFeedIndex", chan, lasttime));
						
						continue;
					}
					
					// TODO check tags
					
					if (uniqueConsumer.apply(lastid))
						cnt++;
				}
			}
			else {
				if (lasttime == null) 
					lasttime = ByteUtil.extractValue(task.getInterface().nextPeerKey(did, "dcmFeedIndex", chan, null));
				
				while ((cnt < max) && (lasttime != null) && ((totime == null) || (totime.compareTo(((Number) lasttime).longValue()) > 0))) {
					lastid = ByteUtil.extractValue(task.getInterface().nextPeerKey(did, "dcmFeedIndex", chan, lasttime, lastid));		// might return null
	
					// try the next publish time
					if (lastid == null) {
						lasttime = ByteUtil.extractValue(task.getInterface().nextPeerKey(did, "dcmFeedIndex", chan, lasttime));
						
						continue;
					}
					
					// TODO check tags
					
					if (uniqueConsumer.apply(lastid))
						cnt++;
				}
			}
		}
		catch (Exception x) {
			Logger.error("Error scanning feed index: " + x);
		}
	}
}
