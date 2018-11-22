package dcraft.cms.feed.db;

import dcraft.cms.util.FeedUtil;
import dcraft.db.DatabaseAdapter;
import dcraft.db.DatabaseException;
import dcraft.db.proc.filter.CurrentRecord;
import dcraft.db.proc.filter.Unique;
import dcraft.db.tables.TablesAdapter;
import dcraft.filestore.CommonPath;
import dcraft.filestore.FileStore;
import dcraft.filestore.FileStoreFile;
import dcraft.filestore.local.LocalStore;
import dcraft.filestore.mem.MemoryStoreFile;
import dcraft.filevault.Vault;
import dcraft.filevault.VaultUtil;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
import dcraft.hub.op.OperationMarker;
import dcraft.hub.op.OperationOutcome;
import dcraft.hub.op.OperationOutcomeEmpty;
import dcraft.hub.op.OperationOutcomeStruct;
import dcraft.locale.LocaleUtil;
import dcraft.log.Logger;
import dcraft.script.ScriptHub;
import dcraft.struct.ListStruct;
import dcraft.struct.RecordStruct;
import dcraft.struct.Struct;
import dcraft.util.StringUtil;
import dcraft.util.TimeUtil;
import dcraft.xml.XElement;
import dcraft.xml.XmlReader;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

public class FeedUtilDb {
	static public void updateFeedIndex(TablesAdapter db, String path) throws OperatingContextException {
		if (! path.endsWith(".html"))
			return;

		CommonPath opath = CommonPath.from("/" + OperationContext.getOrThrow().getSite().getAlias() + path.substring(0, path.length() - 5));
		CommonPath ochan = opath.subpath(0, 2);		// site and feed

		String oid = Struct.objectToString(db.firstInIndex("dcmFeed", "dcmPath", opath, false));

		ZonedDateTime opubtime = null;

		RecordStruct fields = RecordStruct.record();

		if (oid == null) {
			oid = db.createRecord("dcmFeed");

			fields
					.with("dcmAlias", RecordStruct.record()
							.with("Data", opath.getName(1))
					)
					.with("dcmPath", RecordStruct.record()
							.with("Data", opath)
					)
					// TODO use settings to determine start path, should be null if not an entry (such as a block)
					.with("dcmLocalPath", RecordStruct.record()
							.with("Data", "pages".equals(opath.getName(1)) ? opath.subpath(2) : opath.subpath(1))
					)
					.with("dcmAuthor", RecordStruct.record()
							.with("Data", OperationContext.getOrThrow().getUserContext().getUserId())
					);
		}
		else {
			if (db.isRetired("dcmFeed", oid))
				db.reviveRecord("dcmFeed", oid);
			
			opubtime = Struct.objectToDateTime(db.getStaticScalar("dcmFeed", oid, "dcmPublishAt"));
		}

		fields
				.with("dcmModified", RecordStruct.record()
						.with("Data", TimeUtil.now())
				);

		Vault fvault = OperationContext.getOrThrow().getSite().getVault("Feeds");

		if (fvault == null) {
			Logger.error("Missing Feeds vault");
			return;
		}

		FileStore fs = fvault.getFileStore();

		if (!(fs instanceof LocalStore)) {
			Logger.error("Feeds vault must be local");  // for now
			return;
		}

		LocalStore lfs = (LocalStore) fs;

		// feeds use site default
		String defloc = OperationContext.getOrThrow().getSite().getResources().getLocale().getDefaultLocale();

		XElement root = XmlReader.loadFile(lfs.resolvePath(path), true, true);

		if (root == null) {
			Logger.error("Unable to feed index: " + path);
			return;
		}

		RecordStruct feedDef = FeedUtil.getFeedDefinition(opath.getName(1));

		// shared fields
		{
			List<String> oldkeys = db.getStaticListKeys("dcmFeed", oid, "dcmSharedFields");

			RecordStruct newkeys = RecordStruct.record();

			for (XElement meta : root.selectAll("Meta")) {
				if (meta.hasEmptyAttribute("Name"))
					continue;

				String name = meta.getAttribute("Name");

				FeedUtil.FieldType ftype = FeedUtil.getFieldType(feedDef, name);

				if (ftype != FeedUtil.FieldType.Shared)
					continue;

				String value = meta.getValue();

				newkeys
						.with(name , RecordStruct.record()
								.with("UpdateOnly", true)
								.with("Data", value)
						);

				oldkeys.remove(name);

				if ("PublishAt".equals(name)) {
					ZonedDateTime data = null;

					if (value.length() > 10) {
						data = Struct.objectToDateTime(value);
					}
					else {
						// TODO enhance to support feed timezone (feed wide, record level)
						data = Struct.objectToDate(value).atStartOfDay(ZoneId.of(OperationContext.getOrThrow().getChronology()));
					}

					fields.with("dcmPublishAt", RecordStruct.record()
							.with("UpdateOnly", true)
							.with("Data", data)
					);
				}
			}

			// the remaining should be retired
			for (String key : oldkeys) {
				newkeys
						.with(key, RecordStruct.record()
								.with("UpdateOnly", true)
								.with("Retired", true)
						);

				// un-publish if removed
				if ("PublishAt".equals(key))
					fields.with("dcmPublishAt", RecordStruct.record()
							.with("UpdateOnly", true)
							.with("Retired", true)
					);
			}

			fields.with("dcmSharedFields", newkeys);
		}

		// locale fields
		{
			List<String> oldkeys = db.getStaticListKeys("dcmFeed", oid, "dcmLocaleFields");

			RecordStruct newkeys = RecordStruct.record();

			for (XElement meta : root.selectAll("Meta")) {
				if (meta.hasEmptyAttribute("Name"))
					continue;

				String name = meta.getAttribute("Name");

				FeedUtil.FieldType ftype = FeedUtil.getFieldType(feedDef, name);

				if (ftype != FeedUtil.FieldType.Locale)
					continue;

				for (XElement tr : meta.selectAll("Tr")) {
					String locale = LocaleUtil.normalizeCode(tr.getAttribute("Locale", defloc));

					String key= name + "." + locale;

					newkeys
							.with(key, RecordStruct.record()
									.with("UpdateOnly", true)
									.with("Lang", locale)
									.with("Data", tr.getValue())
							);

					oldkeys.remove(key);
				}

				if (meta.hasValue()) {
					String key= name + "." + defloc;

					newkeys
							.with(key, RecordStruct.record()
									.with("UpdateOnly", true)
									.with("Data", meta.getValue())
							);

					oldkeys.remove(key);
				}
			}

			// the remaining should be retired
			for (String key : oldkeys) {
				newkeys
						.with(key, RecordStruct.record()
								.with("UpdateOnly", true)
								.with("Retired", true)
						);
			}

			fields.with("dcmLocaleFields", newkeys);
		}

		// validate, normalize and store
		if (db.checkFieldsInternal("dcmFeed", fields, oid)) {
			db.setFieldsInternal("dcmFeed", oid, fields);

			ZonedDateTime npubtime = Struct.objectToDateTime(db.getStaticScalar("dcmFeed", oid, "dcmPublishAt"));

			// set index

			try {
				// only kill if needed
				if ((opubtime != null) && ! opubtime.equals(npubtime))
					db.getRequest().getInterface().kill(OperationContext.getOrThrow().getUserContext().getTenantAlias(),
							"dcmFeedIndex", ochan.toString(), db.getRequest().getInterface().inverseTime(opubtime), oid);

				if (npubtime != null)
					db.getRequest().getInterface().set(OperationContext.getOrThrow().getUserContext().getTenantAlias(),
							"dcmFeedIndex", ochan.toString(), db.getRequest().getInterface().inverseTime(npubtime), oid, "");   // TODO to tags
			}
			catch (Exception x) {
				Logger.error("Error updating feed index: " + x);
			}
		}
		else {
			Logger.error("Unable to update feed table or index");
		}
	}

	static public void deleteFeedIndex(DatabaseAdapter conn, TablesAdapter db, String path) throws OperatingContextException {
		CommonPath opath = FeedUtilDb.toIndexPath(path);
		
		if (opath == null)
			return;
		
		CommonPath ochan = opath.subpath(0, 2);		// site and feed

		Object oid = db.firstInIndex("dcmFeed", "dcmPath", opath, true);

		if (oid == null)
			return;

		// delete from dcmFeedIndex
		ZonedDateTime opubtime = Struct.objectToDateTime(db.getStaticScalar("dcmFeed", oid.toString(),"dcmPublishAt"));

		try {
			conn.kill(OperationContext.getOrThrow().getUserContext().getTenantAlias(),
					"dcmFeedIndex", ochan, conn.inverseTime(opubtime), oid);
		}
		catch (DatabaseException x) {
			Logger.error("Error killing global: " + x);
		}

		db.retireRecord("dcmFeed", oid.toString());
	}
	
	// Command History
	
	static public CommonPath toIndexPath(String feed, String path) throws OperatingContextException {
		return FeedUtilDb.toIndexPath("/" + feed + path);
	}
	
	static public CommonPath toIndexPath(String path) throws OperatingContextException {
		if (path.endsWith(".html"))
			path = path.substring(0, path.length() - 5);
		
		return CommonPath.from("/" + OperationContext.getOrThrow().getSite().getAlias() + path);
	}
	
	static public String findHistory(DatabaseAdapter conn, TablesAdapter db, String feed, String path, boolean create, boolean audit) throws OperatingContextException {
		CommonPath epath = FeedUtilDb.toIndexPath(feed, path);
		
		Unique collector = (Unique) db.traverseIndex(OperationContext.getOrThrow(), "dcmFeedHistory", "dcmPath", epath.toString(), Unique.unique().withNested(
				CurrentRecord.current().withNested(HistoryFilter.forDraft())));
		
		String hid = collector.isEmpty() ? null : collector.getOne().toString();
		
		if (hid == null) {
			if (create) {
				hid = db.createRecord("dcmFeedHistory");
				
				db.setStaticScalar("dcmFeedHistory", hid, "dcmPath", epath);
				db.setStaticScalar("dcmFeedHistory", hid, "dcmStartedAt", TimeUtil.now());
				db.setStaticScalar("dcmFeedHistory", hid, "dcmStartedBy", OperationContext.getOrThrow().getUserContext().getUserId());
			}
		}
		else {
			if (audit) {
				db.setStaticScalar("dcmFeedHistory", hid, "dcmModifiedAt", TimeUtil.now());
				db.setStaticScalar("dcmFeedHistory", hid, "dcmModifiedBy", OperationContext.getOrThrow().getUserContext().getUserId());
			}
		}
		
		return hid;
	}
	
	static public void addHistory(DatabaseAdapter conn, TablesAdapter db, String feed, String path, ListStruct commands) throws OperatingContextException {
		String hid = FeedUtilDb.findHistory(conn, db, feed, path, true, true);
		
		if (commands != null) {
			ZonedDateTime stamp = TimeUtil.now().minusSeconds(1);
			
			for (Struct cstruct : commands.items()) {
				db.setStaticList("dcmFeedHistory", hid, "dcmModifications", TimeUtil.stampFmt.format(stamp), cstruct);
				
				// forward 1 ms
				stamp = stamp.plusNanos(1000000);
			}
		}
		
		// TODO publish - if dcmScheduleAt then just set the dcmScheduled field to dcmScheduleAt, else do it now
	}
	
	static public void discardHistory(DatabaseAdapter conn, TablesAdapter db, String feed, String path, RecordStruct data, OperationOutcomeStruct callback) throws OperatingContextException {
		String hid = FeedUtilDb.findHistory(conn, db, feed, path, false, false);
		
		if (hid != null) {
			db.setStaticScalar("dcmFeedHistory", hid, "dcmCancelled", true);
			db.setStaticScalar("dcmFeedHistory", hid, "dcmCancelledAt", TimeUtil.now());
			db.setStaticScalar("dcmFeedHistory", hid, "dcmCancelledBy", OperationContext.getOrThrow().getUserContext().getUserId());
			
			if ((data != null) && data.hasField("Note"))
				db.setStaticScalar("dcmFeedHistory", hid, "dcmNote", data.getFieldAsString("Note"));
		}
		else {
			Logger.warn("Could not find any feed history to discard");
		}
		
		callback.returnEmpty();
	}
	
	static public void saveHistory(DatabaseAdapter conn, TablesAdapter db, String feed, String path, RecordStruct data, boolean publish, OperationOutcomeStruct callback) throws OperatingContextException {
		String hid = FeedUtilDb.findHistory(conn, db, feed, path, true, true);
		
		if ((data != null) && data.hasField("Note"))
			db.setStaticScalar("dcmFeedHistory", hid, "dcmNote", data.getFieldAsString("Note"));
		
		if (publish) {
			FeedUtilDb.publishHistory(conn, db, feed, path, hid, callback);
		}
		else {
			callback.returnEmpty();
		}
		
	}
	
	static public void publishHistory(DatabaseAdapter conn, TablesAdapter db, String feed, String path, String hid, OperationOutcomeStruct callback) throws OperatingContextException {
		db.setStaticScalar("dcmFeedHistory", hid, "dcmPublished", true);
		
		Vault feedsvault = OperationContext.getOrThrow().getSite().getVault("Feeds");
		
		// TODO feedsvault.mapRequest ...
		
		FileStoreFile fileStoreFile = feedsvault.getFileStore().fileReference(
				CommonPath.from("/" + feed + path));
		
		fileStoreFile.readAllText(new OperationOutcome<String>() {
			@Override
			public void callback(String result) throws OperatingContextException {
				if (this.isNotEmptyResult()) {
					XElement root = ScriptHub.parseInstructions(result);
					
					if (root == null) {
						Logger.error("Feed file not well formed XML");
						callback.returnEmpty();
						return;
					}
					
					CommonPath epath = FeedUtilDb.toIndexPath(feed, path);
					
					for (String key : db.getStaticListKeys("dcmFeedHistory", hid, "dcmModifications")) {
						RecordStruct command = Struct.objectToRecord(db.getStaticList("dcmFeedHistory", hid, "dcmModifications", key));
						
						// check null, modification could be retired
						if (command != null) {
							try (OperationMarker om = OperationMarker.create()) {
								FeedUtil.applyCommand(epath, root, command, false);
								
								if (om.hasErrors()) {
									// TODO break/skip
								}
							}
							catch (Exception x) {
								Logger.error("OperationMarker error - applying history");
								// TODO break/skip
							}
						}
					}
					
					// if not marked as published - do so, we are publishing
					if (StringUtil.isEmpty(FeedUtil.getSharedField("PublishAt", root)))
						FeedUtil.updateSharedField("PublishAt", LocalDate.now().toString(), root);
					
					// save part as deposit
					MemoryStoreFile msource = MemoryStoreFile.of(fileStoreFile.getPathAsCommon())
							.with(root.toPrettyString());
					
					VaultUtil.transfer("Feeds", msource, fileStoreFile.getPathAsCommon(), null, new OperationOutcomeStruct() {
						@Override
						public void callback(Struct result) throws OperatingContextException {
							if (! this.hasErrors()) {
								db.setStaticScalar("dcmFeedHistory", hid, "dcmCompleted", true);
								db.setStaticScalar("dcmFeedHistory", hid, "dcmCompletedAt", TimeUtil.now());
								
								// TODO publish - if dcmScheduleAt then just set the dcmScheduled field to dcmScheduleAt, else do it now
							}
							
							callback.returnEmpty();
						}
					});
				}
				else {
					Logger.error("Unable to publish to empty file");
					callback.returnEmpty();
				}
			}
		});
	}
}