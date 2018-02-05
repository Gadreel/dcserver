package dcraft.cms.thread.db;

import dcraft.db.DatabaseException;
import dcraft.db.IRequestContext;
import dcraft.db.proc.ExpressionResult;
import dcraft.db.proc.IFilter;
import dcraft.db.tables.TablesAdapter;
import dcraft.db.util.ByteUtil;
import dcraft.hub.ResourceHub;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
import dcraft.hub.time.BigDateTime;
import dcraft.log.Logger;
import dcraft.schema.DataType;
import dcraft.schema.DbField;
import dcraft.schema.SchemaResource;
import dcraft.struct.ListStruct;
import dcraft.struct.RecordStruct;
import dcraft.struct.Struct;
import dcraft.util.HashUtil;
import dcraft.util.RndUtil;
import dcraft.util.StringUtil;
import dcraft.util.TimeUtil;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.List;

import static dcraft.db.Constants.DB_GLOBAL_INDEX_SUB;

/*
 * Messages are typically in /InBox, /Archive or /Trash
 *
 * If you are a sender then generally your sent message is in /Archive
 * There is no /Sent folder (typically) but you can use Origin to find if needed, but generally looking in /Archive to find messages
 * that are not in InBox.
 *
 */
public class ThreadUtil {
	/*
		General methods
	 */
	static public String getThreadId(TablesAdapter db, RecordStruct params) throws OperatingContextException {
		String tid = params.getFieldAsString("Id");

		if (StringUtil.isEmpty(tid) && !params.isFieldEmpty("Uuid")) {
			String uuid = params.getFieldAsString("Uuid");

			Object oid = db.firstInIndex("dcmThread", "dcmUuid", uuid);

			if (oid != null)
				tid = oid.toString();
		}

		if (StringUtil.isEmpty(tid) && !params.isFieldEmpty("Hash")) {
			String hash = params.getFieldAsString("Hash");

			Object oid = db.firstInIndex("dcmThread", "dcmHash", hash);

			if (oid != null)
				tid = oid.toString();
		}

		return tid;
	}

	/*
		Create, populate methods
	 */
	public static String createThread(TablesAdapter db, String title, String type) throws OperatingContextException {
		return ThreadUtil.createThread(db, title, false, type, null, null, null);
	}
	
	public static String createThread(TablesAdapter db, String title, String type, String from) throws OperatingContextException {
		return ThreadUtil.createThread(db, title, false, type, from, null, null);
	}
	
	public static String createThread(TablesAdapter db, String title, boolean trackTitle, String type, String from, ZonedDateTime deliver, ZonedDateTime end) throws OperatingContextException {
		if (StringUtil.isNotEmpty(title))
			title = title.trim();
		
		// TODO figure out how to send to future date (target date vs modified)
		
		String uuid = RndUtil.nextUUId();
		
		String hash = HashUtil.getSha256((trackTitle && StringUtil.isNotEmpty(title)) ? title : uuid);
		ZonedDateTime now = TimeUtil.now();
		
		if (deliver == null)
			deliver = now;
		
		String originator = StringUtil.isNotEmpty(from) ? from : OperationContext.getOrThrow().getUserContext().getUserId();
		
		String id = db.createRecord("dcmThread");
		
		db.setStaticScalar("dcmThread", id, "dcmTitle", title);
		db.setStaticScalar("dcmThread", id, "dcmHash", hash);
		db.setStaticScalar("dcmThread", id, "dcmUuid", uuid);
		db.setStaticScalar("dcmThread", id, "dcmMessageType", type);
		db.setStaticScalar("dcmThread", id, "dcmCreated", now);
		db.setStaticScalar("dcmThread", id, "dcmModified", deliver);				// we show threads ordered by modified, when new content is added modified changes
		db.setStaticScalar("dcmThread", id, "dcmOriginator", originator);
		db.setStaticScalar("dcmThread", id, "dcmTargetDate", deliver);
		
		if (end != null)
			db.setStaticScalar("dcmThread", id, "dcmEndDate", end);
		
		return id;
	}
	
	public static void assignLabels(TablesAdapter db, String id, ListStruct labels) throws OperatingContextException {
		if ((labels != null) && ! labels.isEmpty())
			db.setStaticScalar("dcmThread", id, "dcmLabels", "|" + StringUtil.join(labels.toStringList(), "|") + "|");
	}

	public static void setParties(TablesAdapter db, String id, ListStruct parties) throws OperatingContextException {
		if ((parties != null) && ! parties.isEmpty()) {
			for (int i = 0; i < parties.size(); i++) {
				RecordStruct party = parties.getItemAsRecord(i);

				ThreadUtil.addParty(db, id, party.getFieldAsString("Party"), party.getFieldAsString("Folder"), party.getFieldAsList("PartyLabels"));
			}
		}
	}

	public static void addParty(TablesAdapter db, String id, String ident, String folder, ListStruct labels) throws OperatingContextException {
		if (StringUtil.isEmpty(ident))
			return;

		db.updateStaticList("dcmThread", id, "dcmParty", ident, ident);

		if (StringUtil.isNotEmpty(folder))
			db.updateStaticList("dcmThread", id, "dcmFolder", ident, folder);

		if ((labels != null) && ! labels.isEmpty())
			db.updateStaticList("dcmThread", id, "dcmPartyLabels", ident,"|" + StringUtil.join(labels.toStringList(), "|") + "|");
	}

	// does not change dcmModified, do so manually
	public static String addContent(TablesAdapter db, String id, String content) throws OperatingContextException {
		return ThreadUtil.addContent(db, id, content, null, null, null, null);
	}

	public static String addContent(TablesAdapter db, String id, String content, String contentType, String originator) throws OperatingContextException {
		return ThreadUtil.addContent(db, id, content, contentType, originator, null, null);
	}

	public static String addContent(TablesAdapter db, String id, String content, String contentType, String originator, String source, RecordStruct attributes) throws OperatingContextException {
		if (StringUtil.isEmpty(content))
			return null;

		String stamp = TimeUtil.stampFmt.format(TimeUtil.now());

		originator = StringUtil.isNotEmpty(originator) ? originator : OperationContext.getOrThrow().getUserContext().getUserId();

		db.updateStaticList("dcmThread", id, "dcmContent", stamp, content);
		db.updateStaticList("dcmThread", id, "dcmContentHash", stamp, HashUtil.getSha256(content));
		db.updateStaticList("dcmThread", id, "dcmContentType", stamp, StringUtil.isNotEmpty(contentType) ? contentType : "UnsafeMD");
		db.updateStaticList("dcmThread", id, "dcmContentOriginator", stamp, originator);

		if (StringUtil.isNotEmpty(source))
			db.updateStaticList("dcmThread", id, "dcmContentSource", stamp, source);

		if ((attributes != null) && ! attributes.isEmpty())
			db.updateStaticList("dcmThread", id, "dcmContentAttributes", stamp,attributes);

		return stamp;
	}

	public static void deliver(TablesAdapter db, String id, ZonedDateTime deliver) throws OperatingContextException {
		try {
			String tenant = OperationContext.getOrThrow().getTenant().getAlias();

			db.updateStaticScalar("dcmThread", id, "dcmModified", deliver);				// we show threads ordered by modified, when new content is added modified changes

			BigDecimal revmod = ByteUtil.dateTimeToReverse(deliver);

			List<String> parties = db.getStaticListKeys("dcmThread", id, "dcmParty");

			for (String party : parties) {
				String folder = Struct.objectToString(db.getStaticList("dcmThread", id, "dcmFolder", party));
				Boolean isread = Struct.objectToBooleanOrFalse(db.getStaticList("dcmThread", id, "dcmRead", party));

				db.getRequest().getInterface().set(tenant, "dcmThreadA", party, folder, revmod, id, isread);
			}
		}
		catch (DatabaseException x) {
			Logger.error("Unable to deliver thread: " + x);
		}
	}
	
	public static void deliveryNotice(TablesAdapter db, String id) throws OperatingContextException {
		// TODO
	}
	
	/*
		section modify existing threads
	 */
	
	public static void updateDeliver(TablesAdapter db, String id, ZonedDateTime deliver) throws OperatingContextException {
		try {
			String tenant = OperationContext.getOrThrow().getTenant().getAlias();
			
			ZonedDateTime olddeliver = Struct.objectToDateTime(db.getStaticScalar("dcmThread", id, "dcmModified"));
			
			BigDecimal oldrevmod = ByteUtil.dateTimeToReverse(olddeliver);
			
			db.updateStaticScalar("dcmThread", id, "dcmModified", deliver);				// we show threads ordered by modified, when new content is added modified changes
			
			BigDecimal revmod = ByteUtil.dateTimeToReverse(deliver);
			
			if (! revmod.equals(oldrevmod)) {
				List<String> parties = db.getStaticListKeys("dcmThread", id, "dcmParty");
				
				for (String party : parties) {
					String folder = Struct.objectToString(db.getStaticList("dcmThread", id, "dcmFolder", party));
					Boolean isread = Struct.objectToBooleanOrFalse(db.getStaticList("dcmThread", id, "dcmRead", party));
					
					db.getRequest().getInterface().kill(tenant, "dcmThreadA", party, folder, oldrevmod, id);
					
					db.getRequest().getInterface().set(tenant, "dcmThreadA", party, folder, revmod, id, isread);
				}
			}
		}
		catch (DatabaseException x) {
			Logger.error("Unable to deliver thread: " + x);
		}
	}

	public static void updateFolder(TablesAdapter db, String id, String party, String folder, Boolean isRead) throws OperatingContextException {
		try {
			String tenant = OperationContext.getOrThrow().getTenant().getAlias();

			ZonedDateTime olddeliver = Struct.objectToDateTime(db.getStaticScalar("dcmThread", id, "dcmModified"));

			BigDecimal oldrevmod = ByteUtil.dateTimeToReverse(olddeliver);

			String oldfolder = Struct.objectToString(db.getStaticList("dcmThread", id, "dcmFolder", party));
			
			db.updateStaticList("dcmThread", id, "dcmFolder", party, folder);

			if (isRead == null) {
				isRead = Struct.objectToBooleanOrFalse(db.getStaticList("dcmThread", id, "dcmRead", party));
			}
			else {
				db.updateStaticList("dcmThread", id, "dcmRead", party,isRead);
			}
			
			db.getRequest().getInterface().kill(tenant, "dcmThreadA", party, oldfolder, oldrevmod, id);

			db.getRequest().getInterface().set(tenant, "dcmThreadA", party, folder, oldrevmod, id, isRead);
		}
		catch (DatabaseException x) {
			Logger.error("Unable to deliver thread: " + x);
		}
	}

	public static void deleteThread(TablesAdapter db, String id) throws OperatingContextException {
		try {
			String tenant = OperationContext.getOrThrow().getTenant().getAlias();

			ZonedDateTime olddeliver = Struct.objectToDateTime(db.getStaticScalar("dcmThread", id, "dcmModified"));

			BigDecimal oldrevmod = ByteUtil.dateTimeToReverse(olddeliver);

			List<String> parties = db.getStaticListKeys("dcmThread", id, "dcmParty");

			for (String party : parties) {
				String folder = Struct.objectToString(db.getStaticList("dcmThread", id, "dcmFolder", party));

				db.getRequest().getInterface().kill(tenant, "dcmThreadA", party, folder, oldrevmod, id);
			}

			db.setStaticScalar("dcmThread", id, "Retired", true);
		}
		catch (DatabaseException x) {
			Logger.error("Unable to deliver thread: " + x);
		}
	}

	static public void traverseThreadIndex(IRequestContext request, TablesAdapter db, String party, String folder, IFilter out) throws OperatingContextException {
		String did = request.getTenant();

		if (StringUtil.isEmpty(party) || StringUtil.isEmpty(folder))
			return;

		try {
			// start now and work backward
			Object vid = ByteUtil.dateTimeToReverse(TimeUtil.now());

			byte[] revid = request.getInterface().getOrNextPeerKey(did, "dcmThreadA", party, folder, vid);

			while (revid != null) {
				vid = ByteUtil.extractValue(revid);

				byte[] recid = request.getInterface().nextPeerKey(did, "dcmThreadA", party, folder, vid, null);

				while (recid != null) {
					Object rid = ByteUtil.extractValue(recid);

					ExpressionResult filterResult = out.check(db, rid);

					if (! filterResult.resume)
						return;

					recid = request.getInterface().nextPeerKey(did, "dcmThreadA", party, folder, vid, rid);
				}

				revid = request.getInterface().nextPeerKey(did, "dcmThreadA", party, folder, vid);
			}
		}
		catch (Exception x) {
			Logger.error("traverseThreadIndex error: " + x);
		}
	}
}
