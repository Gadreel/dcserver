package dcraft.cms.thread.db;

import dcraft.cms.thread.IChannelAccess;
import dcraft.db.DatabaseException;
import dcraft.db.IRequestContext;
import dcraft.db.proc.ExpressionResult;
import dcraft.db.proc.IFilter;
import dcraft.db.proc.filter.CurrentRecord;
import dcraft.db.proc.filter.Unique;
import dcraft.db.tables.TableUtil;
import dcraft.db.tables.TablesAdapter;
import dcraft.db.util.ByteUtil;
import dcraft.filestore.CommonPath;
import dcraft.hub.ResourceHub;
import dcraft.hub.app.ApplicationHub;
import dcraft.hub.op.IVariableAware;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
import dcraft.log.Logger;
import dcraft.struct.ListStruct;
import dcraft.struct.RecordStruct;
import dcraft.struct.Struct;
import dcraft.task.Task;
import dcraft.task.TaskHub;
import dcraft.util.HashUtil;
import dcraft.util.RndUtil;
import dcraft.util.StringUtil;
import dcraft.util.TimeUtil;
import dcraft.xml.XElement;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

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

		if (StringUtil.isNotEmpty(tid) && db.isCurrent("dcmThread", tid))
			return tid;

		if (StringUtil.isEmpty(tid) && ! params.isFieldEmpty("Uuid")) {
			String uuid = params.getFieldAsString("Uuid");

			Object oid = db.firstInIndex("dcmThread", "dcmUuid", uuid, true);

			if (oid != null)
				tid = oid.toString();
		}

		if (StringUtil.isEmpty(tid) && ! params.isFieldEmpty("Hash")) {
			String hash = params.getFieldAsString("Hash");

			Object oid = db.firstInIndex("dcmThread", "dcmHash", hash, true);

			if (oid != null)
				tid = oid.toString();
		}

		return tid;
	}

	static public String getThreadId(TablesAdapter db, String uuid) throws OperatingContextException {
		return Struct.objectToString(db.firstInIndex("dcmThread", "dcmUuid", uuid, true));
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
		
		db.setScalar("dcmThread", id, "dcmTitle", title);
		db.setScalar("dcmThread", id, "dcmHash", hash);
		db.setScalar("dcmThread", id, "dcmUuid", uuid);
		db.setScalar("dcmThread", id, "dcmMessageType", type);
		db.setScalar("dcmThread", id, "dcmCreated", now);
		db.setScalar("dcmThread", id, "dcmModified", deliver);				// we show threads ordered by modified, when new content is added modified changes
		db.setScalar("dcmThread", id, "dcmOriginator", originator);
		db.setScalar("dcmThread", id, "dcmTargetDate", deliver);
		
		if (end != null)
			db.setScalar("dcmThread", id, "dcmEndDate", end);
		
		return id;
	}
	
	public static void assignLabels(TablesAdapter db, String id, ListStruct labels) throws OperatingContextException {
		if ((labels != null) && ! labels.isEmpty())
			db.setScalar("dcmThread", id, "dcmLabels", "|" + StringUtil.join(labels.toStringList(), "|") + "|");
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

		db.updateList("dcmThread", id, "dcmParty", ident, ident);

		if (StringUtil.isNotEmpty(folder))
			db.updateList("dcmThread", id, "dcmFolder", ident, folder);

		if ((labels != null) && ! labels.isEmpty())
			db.updateList("dcmThread", id, "dcmPartyLabels", ident,"|" + StringUtil.join(labels.toStringList(), "|") + "|");
	}

	// use if msg has already been delivered once to add new people
	public static void addPartyDeliver(TablesAdapter db, String id, String ident, String folder, ListStruct labels) throws OperatingContextException {
		ThreadUtil.addParty(db, id, ident, folder, labels);

		ThreadUtil.deliverNewParty(db, id, ident, folder, false);
	}

	// does not change dcmModified, do so manually
	public static String addContent(TablesAdapter db, String id, String content) throws OperatingContextException {
		return ThreadUtil.addContent(db, id, content, null, null, null, null);
	}

	public static String addContent(TablesAdapter db, String id, String content, String contentType) throws OperatingContextException {
		return ThreadUtil.addContent(db, id, content, contentType, null, null, null);
	}

	public static String addContent(TablesAdapter db, String id, String content, String contentType, String originator, String source, RecordStruct attributes) throws OperatingContextException {
		if (StringUtil.isEmpty(content))
			return null;

		String stamp = TimeUtil.stampFmt.format(TimeUtil.now());

		originator = StringUtil.isNotEmpty(originator) ? originator : Struct.objectToString(db.getScalar("dcmThread", id, "dcmOriginator"));

		db.updateList("dcmThread", id, "dcmContent", stamp, content);
		db.updateList("dcmThread", id, "dcmContentHash", stamp, HashUtil.getSha256(content));
		db.updateList("dcmThread", id, "dcmContentType", stamp, StringUtil.isNotEmpty(contentType) ? contentType : "UnsafeMD");
		db.updateList("dcmThread", id, "dcmContentOriginator", stamp, originator);

		if (StringUtil.isNotEmpty(source))
			db.updateList("dcmThread", id, "dcmContentSource", stamp, source);

		if ((attributes != null) && ! attributes.isEmpty())
			db.updateList("dcmThread", id, "dcmContentAttributes", stamp,attributes);

		return stamp;
	}

	public static void buildContentAndDeliver(String id) throws OperatingContextException {
		// TODO queue instead?
		TaskHub.submit(Task.ofSubtask("Thread message builder", "THREAD")
				.withParams(RecordStruct.record()
						.with("Id", id)
				)
				.withScript("/dcm/threads/build-message-deliver"));
	}
	
	public static void deliver(TablesAdapter db, String id, ZonedDateTime deliver) throws OperatingContextException {
		ThreadUtil.deliver(db, id, deliver, false);
	}

	// TODO there really should be a "Delivered" field indicating that this has been sent - otherwise when we reindex the
	// dcmThreadA we don't know who should be in the the index or not
	public static void deliver(TablesAdapter db, String id, ZonedDateTime deliver, boolean indexonly) throws OperatingContextException {
		try {

			if (! db.isCurrent("dcmThread", id)) {
				Logger.error("Thread not found or retired: " + id);
				return;
			}

			if (deliver == null) {
				Logger.error("Thread missing delivery date: " + id);
				return;
			}

			String tenant = OperationContext.getOrThrow().getTenant().getAlias();

			db.updateScalar("dcmThread", id, "dcmModified", deliver);				// we show threads ordered by modified, when new content is added modified changes

			BigDecimal revmod = ByteUtil.dateTimeToReverse(deliver);

			List<String> parties = db.getListKeys("dcmThread", id, "dcmParty");

			for (String party : parties) {
				String folder = Struct.objectToString(db.getList("dcmThread", id, "dcmFolder", party));
				Boolean isread = Struct.objectToBooleanOrFalse(db.getList("dcmThread", id, "dcmRead", party));

				if (StringUtil.isNotEmpty(party) && StringUtil.isNotEmpty(folder))
					db.getRequest().getInterface().set(tenant, "dcmThreadA", party, folder, revmod, id, isread);
				else
					Logger.error("Thread missing folder or party: " + id);
			}

			if (! indexonly)
				ThreadUtil.deliveryNotice(db, id);
		}
		catch (DatabaseException x) {
			Logger.error("Unable to deliver thread: " + x);
		}
	}

	static public void clearIndex(TablesAdapter db) throws OperatingContextException {
		try {
			String tenant = OperationContext.getOrThrow().getTenant().getAlias();

			db.getRequest().getInterface().kill(tenant, "dcmThreadA");
		}
		catch (DatabaseException x) {
			Logger.error("Unable to clear thread index: " + x);
		}
	}

	public static XElement getMessageTypeDef(String type) {
		List<XElement> types = ResourceHub.getResources().getConfig().getTagListDeep("Threads/Type");

		for (XElement typedef : types) {
			if (type.equals(typedef.getAttribute("Name"))) {
				return typedef;
			}
		}

		return null;
	}

	public static XElement getChannelDefFromParty(String party) {
		List<XElement> channels = ResourceHub.getResources().getConfig().getTagListDeep("Threads/Channel");

		String channel = ThreadUtil.partyToPrefix(party);

		for (XElement chandef : channels) {
			if (channel.equals(chandef.getAttribute("Prefix"))) {
				return chandef;
			}
		}

		return null;
	}

	public static List<XElement> getChannelDefs() {
		return ResourceHub.getResources().getConfig().getTagListDeep("Threads/Channel");
	}
	
	static public String partyToPrefix(String party) {
		party = party.substring(1);
		
		int pos = party.indexOf('/');
		
		if (pos != -1)
			party = party.substring(0, pos);
		
		return party;
	}
	
	public static List<String> getAllAccess(TablesAdapter db, IVariableAware scope, String... accessScope) throws OperatingContextException {
		List<String> list = new ArrayList<>();

		List<XElement> channels = ResourceHub.getResources().getConfig().getTagListDeep("Threads/Channel");
		
		for (XElement channel : channels) {
			if (channel.hasNotEmptyAttribute("Scopes")) {
				String[] scopes = channel.getAttribute("Scopes").split(",");
				
				if (scopes.length > 0) {
					boolean fnd = false;
					
					for (int i1 = 0; i1 < scopes.length; i1++) {
						String s1 = scopes[i1].trim();
						
						for (int i2 = 0; i2 < accessScope.length; i2++) {
							if (s1.equals(accessScope[i2])) {
								fnd = true;
								break;
							}
						}
						
						if (fnd)
							break;
					}
					
					if (!fnd)
						continue;
				}
			}
			
			list.addAll(ThreadUtil.getChannelAccess(db, scope, channel));
		}
	
		return list;
	}
	
	public static List<String> getChannelAccess(TablesAdapter db, IVariableAware scope, XElement chandef) throws OperatingContextException {
		List<String> list = new ArrayList<>();
		
		if (chandef.hasEmptyAttribute("AccessClass"))
			return list;
		
		Object accessClass = ResourceHub.getResources().getClassLoader().getInstance(chandef.attr("AccessClass"));
		
		if (accessClass instanceof IChannelAccess)
			return ((IChannelAccess) accessClass).collectParties(db, scope);
		
		return list;
	}

	public static void deliveryNotice(TablesAdapter db, String id) throws OperatingContextException {
		List<XElement> channels = ResourceHub.getResources().getConfig().getTagListDeep("Threads/Channel");

		ZonedDateTime deliver = Struct.objectToDateTime(db.getScalar("dcmThread", id, "dcmModified"));

		// no notices for future messages, there is no support given at this level for future message notices
		// must handle separately - most likely do an updateDeliver in future with a (now) current date
		if (deliver.isAfter(TimeUtil.now()))
			return;

		String type = Struct.objectToString(db.getScalar("dcmThread", id, "dcmMessageType"));

		XElement typedef = ThreadUtil.getMessageTypeDef(type);

		if (typedef != null) {
			String notices = typedef.getAttribute("Notices", "default");

			if ("no".equals(notices))
				return;

			List<String> parties = db.getListKeys("dcmThread", id, "dcmParty");

			for (String party : parties) {
				String folder = Struct.objectToString(db.getList("dcmThread", id, "dcmFolder", party));

				// currently only notices on InBox are supported
				if (! "/InBox".equals(folder))
					continue;

				String channel = ThreadUtil.partyToPrefix(party);
				
				for (XElement chandef : channels) {
					if (channel.equals(chandef.getAttribute("Prefix"))) {
						String notices2 = chandef.getAttribute("Notices", "no");

						// if neither is yes then next party
						if (! "yes".equals(notices2) && ! "yes".equals(notices))
							break;

						ThreadUtil.sendDeliveryNotice(db, id, party, chandef, typedef);

						break;
					}
				}
			}
		}
	}

	public static void sendDeliveryNotice(TablesAdapter db, String id, String party) throws OperatingContextException {
		List<XElement> channels = ResourceHub.getResources().getConfig().getTagListDeep("Threads/Channel");

		String type = Struct.objectToString(db.getScalar("dcmThread", id, "dcmMessageType"));

		XElement typedef = ThreadUtil.getMessageTypeDef(type);

		if (typedef != null) {
			String notices = typedef.getAttribute("Notices", "default");

			if ("no".equals(notices))
				return;

			String channel = ThreadUtil.partyToPrefix(party);

			for (XElement chandef : channels) {
				if (channel.equals(chandef.getAttribute("Prefix"))) {
					String notices2 = chandef.getAttribute("Notices", "no");

					// if neither is yes then next party
					if (! "yes".equals(notices2) && ! "yes".equals(notices))
						break;

					ThreadUtil.sendDeliveryNotice(db, id, party, chandef, typedef);

					break;
				}
			}
		}
	}

	public static void sendDeliveryNotice(TablesAdapter db, String id, String party, XElement chandef, XElement typedef) throws OperatingContextException {
		//Main main = Main.tag();

		List<String> stamps = db.getListKeys("dcmThread", id, "dcmContent");
		String beststamp = "";

		for (String stamp : stamps) {
			if (stamp.compareTo(beststamp) > 0)
				beststamp = stamp;
		}

		if (StringUtil.isEmpty(beststamp))
			return;

		String originator = Struct.objectToString(db.getList("dcmThread", id, "dcmContentOriginator", beststamp));

		// don't notify sender
		if ("users".equals(chandef.attr("Alias")) && party.endsWith(originator))
			return;

		// this is the old way to send a notice
		String scriptPath = typedef.getAttribute("NotifyWork", "/dcm/threads/message-notify");

		// TODO should be Queued but fix Channel and Type params if so
		TaskHub.submit(Task.ofSubtask("Thread notice sender", "THREAD")
				.withTopic("Batch")
				.withMaxTries(5)
				.withTimeout(10)        // TODO this should be graduated - 10 minutes moving up to 30 minutes if fails too many times
				.withParams(RecordStruct.record()
						.with("Id", id)
						.with("Stamp", beststamp)
						.with("Party", party)
						.with("Channel", chandef)
						.with("Type", typedef)
				)
				.withScript(scriptPath));
	}

	
	/*
		section modify existing threads
	 */
	
	public static void updateDeliver(TablesAdapter db, String id, ZonedDateTime deliver) throws OperatingContextException {
		if (! db.isPresent("dcmThread", id)) {
			Logger.error("Thread not found: " + id);
			return;
		}

		if (deliver == null) {
			Logger.error("Thread missing delivery date: " + id);
			return;
		}

		CommonPath invpath = CommonPath.from("/dc/dcm/threads/" + id);

		String claimid = ApplicationHub.makeLocalClaim(invpath, 5);

		if (StringUtil.isEmpty(claimid)) {
			Logger.warn("Unable to claim thread: " + id);
			return;
		}

		try {
			String tenant = OperationContext.getOrThrow().getTenant().getAlias();

			ZonedDateTime olddeliver = Struct.objectToDateTime(db.getScalar("dcmThread", id, "dcmModified"));

			if (olddeliver == null) {
				Logger.error("Thread missing old delivery date: " + id);
				return;
			}

			BigDecimal oldrevmod = ByteUtil.dateTimeToReverse(olddeliver);
			
			db.updateScalar("dcmThread", id, "dcmModified", deliver);				// we show threads ordered by modified, when new content is added modified changes
			
			BigDecimal revmod = ByteUtil.dateTimeToReverse(deliver);
			
			if (! revmod.equals(oldrevmod)) {
				List<String> parties = db.getListKeys("dcmThread", id, "dcmParty");
				
				for (String party : parties) {
					String folder = Struct.objectToString(db.getList("dcmThread", id, "dcmFolder", party));
					Boolean isread = Struct.objectToBooleanOrFalse(db.getList("dcmThread", id, "dcmRead", party));

					if (StringUtil.isNotEmpty(party) && StringUtil.isNotEmpty(folder)) {
						db.getRequest().getInterface().kill(tenant, "dcmThreadA", party, folder, oldrevmod, id);

						db.getRequest().getInterface().set(tenant, "dcmThreadA", party, folder, revmod, id, isread);
					}
					else {
						Logger.error("Thread missing folder or party: " + id);
					}
				}
			}

			ThreadUtil.deliveryNotice(db, id);
		}
		catch (DatabaseException x) {
			Logger.error("Unable to deliver thread: " + x);
		}
		finally {
			ApplicationHub.releaseLocalClaim(invpath, claimid);
		}
	}


	public static void deliverNewParty(TablesAdapter db, String id, String party, String folder, boolean isread) throws OperatingContextException {
		if (! db.isPresent("dcmThread", id)) {
			Logger.error("Thread not found: " + id);
			return;
		}

		if (StringUtil.isEmpty(party)) {
			Logger.error("Thread missing party: " + id);
			return;
		}

		if (StringUtil.isEmpty(folder)) {
			Logger.error("Thread missing folder: " + id);
			return;
		}

		CommonPath invpath = CommonPath.from("/dc/dcm/threads/" + id);

		String claimid = ApplicationHub.makeLocalClaim(invpath, 5);

		if (StringUtil.isEmpty(claimid)) {
			Logger.warn("Unable to claim thread: " + id);
			return;
		}

		try {
			String tenant = OperationContext.getOrThrow().getTenant().getAlias();

			ZonedDateTime deliver = Struct.objectToDateTime(db.getScalar("dcmThread", id, "dcmModified"));

			BigDecimal revmod = ByteUtil.dateTimeToReverse(deliver);

			String oldfolder = Struct.objectToString(db.getList("dcmThread", id, "dcmFolder", party));

			if (StringUtil.isNotEmpty(oldfolder))
				db.getRequest().getInterface().kill(tenant, "dcmThreadA", party, oldfolder, revmod, id);

			db.getRequest().getInterface().set(tenant, "dcmThreadA", party, folder, revmod, id, isread);

			// TODO reconsider
			//ThreadUtil.deliveryNotice(db, id);
		}
		catch (DatabaseException x) {
			Logger.error("Unable to deliver thread: " + x);
		}
		finally {
			ApplicationHub.releaseLocalClaim(invpath, claimid);
		}
	}

	public static void updateFolder(TablesAdapter db, String id, String party, String folder, Boolean isRead) throws OperatingContextException {
		try {
			String tenant = OperationContext.getOrThrow().getTenant().getAlias();

			ZonedDateTime olddeliver = Struct.objectToDateTime(db.getScalar("dcmThread", id, "dcmModified"));

			BigDecimal oldrevmod = ByteUtil.dateTimeToReverse(olddeliver);

			String oldfolder = Struct.objectToString(db.getList("dcmThread", id, "dcmFolder", party));
			
			db.updateList("dcmThread", id, "dcmFolder", party, folder);

			if (isRead == null) {
				isRead = Struct.objectToBooleanOrFalse(db.getList("dcmThread", id, "dcmRead", party));
			}
			else {
				db.updateList("dcmThread", id, "dcmRead", party, isRead);
			}
			
			db.getRequest().getInterface().kill(tenant, "dcmThreadA", party, oldfolder, oldrevmod, id);

			db.getRequest().getInterface().set(tenant, "dcmThreadA", party, folder, oldrevmod, id, isRead);
		}
		catch (DatabaseException x) {
			Logger.error("Unable to deliver thread: " + x);
		}
	}

	public static void clearThreadIndex(TablesAdapter db, String id) throws OperatingContextException {
		try {
			String tenant = OperationContext.getOrThrow().getTenant().getAlias();

			ZonedDateTime olddeliver = Struct.objectToDateTime(db.getScalar("dcmThread", id, "dcmModified"));

			BigDecimal oldrevmod = ByteUtil.dateTimeToReverse(olddeliver);

			List<String> parties = db.getListKeys("dcmThread", id, "dcmParty");

			for (String party : parties) {
				String folder = Struct.objectToString(db.getList("dcmThread", id, "dcmFolder", party));

				db.getRequest().getInterface().kill(tenant, "dcmThreadA", party, folder, oldrevmod, id);
			}
		}
		catch (DatabaseException x) {
			Logger.error("Unable to deliver thread: " + x);
		}
	}

	public static void retireThread(TablesAdapter db, String id) throws OperatingContextException {
		ThreadUtil.clearThreadIndex(db, id);

		TableUtil.retireRecord(db,"dcmThread", id);
	}

	static public void traverseThreadIndex(TablesAdapter db, IVariableAware scope, String party, String folder, IFilter out) throws OperatingContextException {
		IRequestContext request = db.getRequest();
		
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

					ExpressionResult filterResult = out.check(db, scope,"dcmThread", rid);

					if (!filterResult.resume)
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
	
	static public int countMessages(TablesAdapter db, ListStruct parties, String folder) throws OperatingContextException {
		Unique collector = Unique.unique();

		for (int i = 0; i < parties.size(); i++) {
			ThreadUtil.traverseThreadIndex(db, OperationContext.getOrThrow(), parties.getItemAsString(i), folder, CurrentRecord.current().withNested(collector));
		}
	
		return collector.getValues().size();
	}

	static public ListStruct loadMessages(TablesAdapter db, ListStruct parties, String folder) throws OperatingContextException {
		ListStruct resp = ListStruct.list();

		// get a message only once, even if it relates to multiple parties
		Unique collector = Unique.unique();

		for (int i = 0; i < parties.size(); i++) {
			String party = parties.getItemAsString(i);

			Unique collector2 = Unique.unique();
			ThreadUtil.traverseThreadIndex(db, OperationContext.getOrThrow(), party, folder, CurrentRecord.current().withNested(collector2));

			for (Object vid : collector2.getValues()) {
				String id = Struct.objectToString(vid);

				// skip already seen
				if (collector.contains(id))
					continue;

				String oid = Struct.objectToString(db.getScalar("dcmThread", id, "dcmOriginator"));

				resp.with(
						RecordStruct.record()
								.with("Id", id)
								.with("Party", party)
								.with("MessageType", db.getScalar("dcmThread", id, "dcmMessageType"))
								.with("Title", db.getScalar("dcmThread", id, "dcmTitle"))
								.with("Originator", oid)
								.with("OriginatorName", db.getScalar("dcUser", oid, "dcLastName"))
								.with("Modified", db.getScalar("dcmThread", id, "dcmModified"))
								.with("Created", db.getScalar("dcmThread", id, "dcmCreated"))
								.with("Read", Struct.objectToBooleanOrFalse(db.getList("dcmThread", id, "dcmRead", party)))
								.with("Attributes", db.getScalar("dcmThread", id, "dcmSharedAttributes"))
				);
			}

			collector.addAll(collector2);
		}

		return resp;
	}

	public static List<String> collectMessageAccess(TablesAdapter db, IVariableAware scope, String mid) throws OperatingContextException {
		List<String> list = new ArrayList<>();
		
		List<String> parties = db.getListKeys("dcmThread", mid, "dcmParty");
		
		for (String party : parties) {
			XElement chandef = ThreadUtil.getChannelDefFromParty(party);
			
			if ((chandef != null) && ! chandef.hasEmptyAttribute("AccessClass")) {
				Object accessClass = ResourceHub.getResources().getClassLoader().getInstance(chandef.attr("AccessClass"));
				
				if (accessClass instanceof IChannelAccess) {
					List<String> accessparties = ((IChannelAccess) accessClass).collectParties(db, scope);
					
					for (String ap : accessparties) {
						if (ap.equals(party)) {
							list.add(party);
							break;
						}
					}
				}
			}
		}
		
		return list;
	}
	
	static public List<String> formatParties(TablesAdapter db, IVariableAware scope, String id, boolean meToo, boolean noOrigin) throws OperatingContextException {
		List<String> list = new ArrayList<>();
		
		String uid = OperationContext.getOrThrow().getUserContext().getUserId();
		String oid = Struct.objectToString(db.getScalar("dcmThread", id, "dcmOriginator"));

		List<String> parties = db.getListKeys("dcmThread", id, "dcmParty");
		
		for (String party : parties) {
			if (party.startsWith("/Usr/")) {
				String pid = party.substring(5);
				
				if (! meToo && uid.equals(pid))
					continue;
				
				if (noOrigin && oid.equals(pid))
					continue;
			}
			
			// TODO optimize so we don't lookup every time?
			
			XElement chandef = ThreadUtil.getChannelDefFromParty(party);
			
			if ((chandef != null) && ! chandef.hasEmptyAttribute("AccessClass")) {
				Object accessClass = ResourceHub.getResources().getClassLoader().getInstance(chandef.attr("AccessClass"));
				
				if (accessClass instanceof IChannelAccess) {
					list.add(((IChannelAccess) accessClass).formatParty(db, scope, party));
				}
			}
		}
		
		return list;
	}
}
