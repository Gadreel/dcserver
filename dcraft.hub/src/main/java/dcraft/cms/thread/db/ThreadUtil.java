package dcraft.cms.thread.db;

import dcraft.cms.thread.IChannelAccess;
import dcraft.cms.util.FeedUtil;
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
import dcraft.hub.time.BigDateTime;
import dcraft.log.Logger;
import dcraft.mail.SmtpWork;
import dcraft.schema.DataType;
import dcraft.schema.DbField;
import dcraft.schema.SchemaResource;
import dcraft.script.StackUtil;
import dcraft.script.inst.*;
import dcraft.script.inst.doc.Base;
import dcraft.script.inst.ext.SendEmail;
import dcraft.script.inst.ext.SendText;
import dcraft.script.work.ExecuteState;
import dcraft.struct.ListStruct;
import dcraft.struct.RecordStruct;
import dcraft.struct.Struct;
import dcraft.struct.scalar.AnyStruct;
import dcraft.struct.scalar.BinaryStruct;
import dcraft.task.Task;
import dcraft.task.TaskContext;
import dcraft.task.TaskHub;
import dcraft.task.TaskObserver;
import dcraft.util.HashUtil;
import dcraft.util.RndUtil;
import dcraft.util.StringUtil;
import dcraft.util.TimeUtil;
import dcraft.xml.XElement;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.ArrayList;
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

	public static String addContent(TablesAdapter db, String id, String content, String contentType) throws OperatingContextException {
		return ThreadUtil.addContent(db, id, content, contentType, null, null, null);
	}

	public static String addContent(TablesAdapter db, String id, String content, String contentType, String originator, String source, RecordStruct attributes) throws OperatingContextException {
		if (StringUtil.isEmpty(content))
			return null;

		String stamp = TimeUtil.stampFmt.format(TimeUtil.now());

		originator = StringUtil.isNotEmpty(originator) ? originator : Struct.objectToString(db.getStaticScalar("dcmThread", id, "dcmOriginator"));

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

	public static void deliver(TablesAdapter db, String id, ZonedDateTime deliver, boolean indexonly) throws OperatingContextException {
		try {

			if (! db.isCurrent("dcmThread", id)) {
				Logger.error("Thread not found: " + id);
				return;
			}

			if (deliver == null) {
				Logger.error("Thread missing delivery date: " + id);
				return;
			}

			String tenant = OperationContext.getOrThrow().getTenant().getAlias();

			db.updateStaticScalar("dcmThread", id, "dcmModified", deliver);				// we show threads ordered by modified, when new content is added modified changes

			BigDecimal revmod = ByteUtil.dateTimeToReverse(deliver);

			List<String> parties = db.getStaticListKeys("dcmThread", id, "dcmParty");

			for (String party : parties) {
				String folder = Struct.objectToString(db.getStaticList("dcmThread", id, "dcmFolder", party));
				Boolean isread = Struct.objectToBooleanOrFalse(db.getStaticList("dcmThread", id, "dcmRead", party));

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
	
	public static List<String> getChannelAccess(TablesAdapter db, IVariableAware scope, XElement chandef) throws OperatingContextException {
		List<String> list = new ArrayList<>();
		
		if (chandef.hasEmptyAttribute("AccessClass"))
			return list;
		
		Object accessClass = ResourceHub.getResources().getClassLoader().getInstance(chandef.attr("AccessClass"));
		
		if (accessClass instanceof IChannelAccess)
			return ((IChannelAccess) accessClass).collectParties(db, scope);
		
		return list;
	}
	
	public static List<String> collectMessageAccess(TablesAdapter db, IVariableAware scope, String mid) throws OperatingContextException {
		List<String> list = new ArrayList<>();
		
		List<String> parties = db.getStaticListKeys("dcmThread", mid, "dcmParty");
		
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

	public static void deliveryNotice(TablesAdapter db, String id) throws OperatingContextException {
		List<XElement> channels = ResourceHub.getResources().getConfig().getTagListDeep("Threads/Channel");

		ZonedDateTime deliver = Struct.objectToDateTime(db.getStaticScalar("dcmThread", id, "dcmModified"));

		// no notices for future messages, there is no support given at this level for future message notices
		// must handle separately - most likely do an updateDeliver in future with a (now) current date
		if (deliver.isAfter(TimeUtil.now()))
			return;

		String type = Struct.objectToString(db.getStaticScalar("dcmThread", id, "dcmMessageType"));

		XElement typedef = ThreadUtil.getMessageTypeDef(type);

		if (typedef != null) {
			String notices = typedef.getAttribute("Notices", "default");

			if ("no".equals(notices))
				return;

			List<String> parties = db.getStaticListKeys("dcmThread", id, "dcmParty");

			for (String party : parties) {
				String folder = Struct.objectToString(db.getStaticList("dcmThread", id, "dcmFolder", party));

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

		String type = Struct.objectToString(db.getStaticScalar("dcmThread", id, "dcmMessageType"));

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

		List<String> stamps = db.getStaticListKeys("dcmThread", id, "dcmContent");
		String beststamp = "";

		for (String stamp : stamps) {
			if (stamp.compareTo(beststamp) > 0)
				beststamp = stamp;
		}

		if (StringUtil.isEmpty(beststamp))
			return;

		String originator = Struct.objectToString(db.getStaticList("dcmThread", id, "dcmContentOriginator", beststamp));

		// don't notify sender
		if ("users".equals(chandef.attr("Alias")) && party.endsWith(originator))
			return;

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
				.withScript("/dcm/threads/message-notify"));


		/*

		boolean usetext = false;

		// TODO handle other message content types someday
		String content = Struct.objectToString(db.getStaticList("dcmThread", id, "dcmContent", beststamp));

		Instruction sendNotice = SendEmail.tag();

		if (party.startsWith("/Usr/")) {
			String uid = party.substring(5);

			String origin = Struct.objectToString(db.getStaticList("dcmThread", id, "dcmContentOriginator", beststamp));

			// don't notify the sender
			if (uid.equals(origin))
				return;

			String notices = Struct.objectToString(db.getStaticScalar("dcUser", uid, "dcNotices"));

			if ("text".equals(notices)) {
				usetext = true;
				sendNotice = SendText.tag();

				String phone = Struct.objectToString(db.getStaticScalar("dcUser", uid, "dcPhone"));

				if (StringUtil.isEmpty(phone))
					return;

				sendNotice.attr("To", phone);
			}
			else {
				String email = Struct.objectToString(db.getStaticScalar("dcUser", uid, "dcEmail"));

				if (StringUtil.isEmpty(email))
					return;

				sendNotice.attr("To", email);
			}
		}
		else if (chandef.hasNotEmptyAttribute("EmailList")) {
			sendNotice.attr("ToList", chandef.getAttribute("EmailList"));
		}
		else if (chandef.hasNotEmptyAttribute("SMS")) {
			usetext = true;
			sendNotice = SendText.tag();

			sendNotice.attr("To", chandef.getAttribute("SMS"));
		}
		else {
			// no other method currently supported
			return;
		}

		XElement catalog = ApplicationHub.getCatalogSettings("Thread-Notice-" + typedef.getAttribute("Name"));

		if (catalog == null)
			catalog = ApplicationHub.getCatalogSettings("Thread-Notice-Default");

		if (catalog == null)
			return;

		sendNotice.attr("Subject", Struct.objectToString(db.getStaticScalar("dcmThread", id, "dcmTitle")));

		String defloc = OperationContext.getOrThrow().getSite().getResources().getLocale().getDefaultLocale();

		// TODO try to take the recipient's preferred locale into consideration
		XElement wrapper = null;

		if (usetext)
			wrapper = FeedUtil.bestMatch(catalog.selectFirst("TextMessage"), defloc, defloc);

		if (wrapper == null)
			wrapper = FeedUtil.bestMatch(catalog.selectFirst("EmailMessage"), defloc, defloc);

		if (wrapper == null)
			return;

		// we don't want to run this if server is not configured with a wrapper/script
		// by default (in packages) /Usr and /NoticesPool will send notices, except they don't have a wrapper
		// so that is all that turns off notices by default - don't enable unless server/tenant expects it

		main.with(
				Var.tag()
						.attr("Name", "Message")
						.attr("SetTo", content),
				Base.tag("text")
						.attr("Name", "TextEmail")
						.withText(wrapper.getValue()),
				sendNotice
						.attr("TextMessage", "$TextEmail")
		);

		TaskHub.submit(Task.ofSubtask("Thread notice trigger", "THREAD")
				.withParams(RecordStruct.record()
						.with("Id", id)
						.with("Party", party)
				)
				.withWork(StackUtil.of(main))
		);
		*/
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

			ZonedDateTime olddeliver = Struct.objectToDateTime(db.getStaticScalar("dcmThread", id, "dcmModified"));

			if (olddeliver == null) {
				Logger.error("Thread missing old delivery date: " + id);
				return;
			}

			BigDecimal oldrevmod = ByteUtil.dateTimeToReverse(olddeliver);
			
			db.updateStaticScalar("dcmThread", id, "dcmModified", deliver);				// we show threads ordered by modified, when new content is added modified changes
			
			BigDecimal revmod = ByteUtil.dateTimeToReverse(deliver);
			
			if (! revmod.equals(oldrevmod)) {
				List<String> parties = db.getStaticListKeys("dcmThread", id, "dcmParty");
				
				for (String party : parties) {
					String folder = Struct.objectToString(db.getStaticList("dcmThread", id, "dcmFolder", party));
					Boolean isread = Struct.objectToBooleanOrFalse(db.getStaticList("dcmThread", id, "dcmRead", party));

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

			TableUtil.retireRecord(db,"dcmThread", id);
		}
		catch (DatabaseException x) {
			Logger.error("Unable to deliver thread: " + x);
		}
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
	
	static public ListStruct loadMessages(TablesAdapter db, ListStruct parties, String folder) throws OperatingContextException {
		ListStruct resp = ListStruct.list();
		
		for (int i = 0; i < parties.size(); i++) {
			String party = parties.getItemAsString(i);
			Unique collector = Unique.unique();
			
			ThreadUtil.traverseThreadIndex(db, OperationContext.getOrThrow(), party, folder, CurrentRecord.current().withNested(collector));
			
			for (Object vid : collector.getValues()) {
				String id = Struct.objectToString(vid);
				
				String oid = Struct.objectToString(db.getStaticScalar("dcmThread", id, "dcmOriginator"));
				
				resp.with(
						RecordStruct.record()
								.with("Id", id)
								.with("Party", party)
								.with("MessageType", db.getStaticScalar("dcmThread", id, "dcmMessageType"))
								.with("Title", db.getStaticScalar("dcmThread", id, "dcmTitle"))
								.with("Originator", oid)
								.with("OriginatorName", db.getStaticScalar("dcUser", oid, "dgaDisplayName"))
								.with("Modified", db.getStaticScalar("dcmThread", id, "dcmModified"))
								.with("Created", db.getStaticScalar("dcmThread", id, "dcmCreated"))
								.with("Read", Struct.objectToBooleanOrFalse(db.getStaticList("dcmThread", id, "dcmRead", party)))
								.with("Attributes", db.getStaticScalar("dcmThread", id, "dcmSharedAttributes"))
				);
			}
		}
	
		return resp;
	}
}
