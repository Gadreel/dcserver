package dcraft.cms.feed.db;

import dcraft.db.DatabaseAdapter;
import dcraft.db.DatabaseException;
import dcraft.db.proc.IStoredProc;
import dcraft.db.ICallContext;
import dcraft.db.tables.TablesAdapter;
import dcraft.filestore.CommonPath;
import dcraft.filestore.FileStore;
import dcraft.filestore.local.LocalStore;
import dcraft.filevault.Vault;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
import dcraft.hub.op.OperationOutcomeStruct;
import dcraft.hub.time.BigDateTime;
import dcraft.locale.LocaleUtil;
import dcraft.log.Logger;
import dcraft.struct.ListStruct;
import dcraft.struct.RecordStruct;
import dcraft.struct.Struct;
import dcraft.util.TimeUtil;
import dcraft.xml.XElement;
import dcraft.xml.XmlReader;

import java.time.ZonedDateTime;
import java.util.List;

public class Update implements IStoredProc {
	@Override
	public void execute(ICallContext request, OperationOutcomeStruct callback) throws OperatingContextException {
		TablesAdapter db = TablesAdapter.ofNow(request);

		ListStruct updated = request.getDataAsRecord().getFieldAsList("Updated");
		ListStruct deleted = request.getDataAsRecord().getFieldAsList("Deleted");

		if (deleted != null) {
			for (int i = 0; i < deleted.size(); i++) {
				this.delete(request.getInterface(), db, deleted.getItemAsString(i));
			}
		}

		if (updated != null) {
			for (int i = 0; i < updated.size(); i++) {
				this.update(request.getInterface(), db, updated.getItemAsString(i));
			}
		}

		callback.returnEmpty();
	}

	public void update(DatabaseAdapter conn, TablesAdapter db, String path) throws OperatingContextException {
		if (!path.endsWith(".html"))
			return;

		CommonPath opath = CommonPath.from("/" + OperationContext.getOrThrow().getSite().getAlias() + path.substring(0, path.length() - 5));
		CommonPath ochan = opath.subpath(0, 2);		// site and feed

		String oid = Struct.objectToString(db.firstInIndex("dcmFeed", "dcmPath", opath));

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

		String defloc = OperationContext.getOrThrow().getSite().getResources().getLocale().getDefaultLocale();

		XElement root = XmlReader.loadFile(lfs.resolvePath(path), true, true);

		if (root == null) {
			Logger.error("Unable to feed index: " + path);
			return;
		}

		// shared fields
		{
			List<String> oldkeys = db.getStaticListKeys("dcmFeed", oid, "dcmSharedFields");

			RecordStruct newkeys = RecordStruct.record();

			for (XElement meta : root.selectAll("Meta")) {
				if (meta.hasEmptyAttribute("Name") || meta.hasEmptyAttribute("Value"))
					continue;

				String name = meta.getAttribute("Name");

				newkeys
						.with(name , RecordStruct.record()
								.with("UpdateOnly", true)
								.with("Data", meta.getAttribute("Value"))
						);

				oldkeys.remove(name);

				if ("PublishAt".equals(name))
					fields.with("dcmPublishAt", RecordStruct.record()
							.with("UpdateOnly", true)
							.with("Data", meta.getAttribute("Value"))
					);
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
				if (meta.hasEmptyAttribute("Name") || meta.hasNotEmptyAttribute("Value"))
					continue;

				String name = meta.getAttribute("Name");

				for (XElement tr : meta.selectAll("Tr")) {
					String locale = LocaleUtil.normalizeCode(tr.getAttribute("Locale", defloc));

					name = name + "." + locale;

					if (tr.hasNotEmptyAttribute("Value"))
						newkeys
								.with(name, RecordStruct.record()
										.with("UpdateOnly", true)
										.with("Lang", locale)
										.with("Data", tr.getAttribute("Value"))
								);
					else
						newkeys
								.with(name, RecordStruct.record()
										.with("UpdateOnly", true)
										.with("Retired", true)
								);
				}

				oldkeys.remove(name);
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
		if (db.checkFields("dcmFeed", fields, oid)) {
			db.setFields("dcmFeed", oid, fields);

			ZonedDateTime npubtime = Struct.objectToDateTime(db.getStaticScalar("dcmFeed", oid, "dcmPublishAt"));

			// set index

			try {
				// only kill if needed
				if ((opubtime != null) && ! opubtime.equals(npubtime))
					conn.kill(OperationContext.getOrThrow().getUserContext().getTenantAlias(),
							"dcmFeedIndex", ochan.toString(), conn.inverseTime(opubtime), oid);

				if (npubtime != null)
					conn.set(OperationContext.getOrThrow().getUserContext().getTenantAlias(),
							"dcmFeedIndex", ochan.toString(), conn.inverseTime(npubtime), oid, "");   // TODO to tags
			}
			catch (Exception x) {
				Logger.error("Error updating feed index: " + x);
			}
		}
		else {
			Logger.error("Unable to update feed table or index");
		}
	}

	public void delete(DatabaseAdapter conn, TablesAdapter db, String path) throws OperatingContextException {
		if (! path.endsWith(".html"))
			return;

		CommonPath opath = CommonPath.from("/" + OperationContext.getOrThrow().getSite().getAlias() + path);
		CommonPath ochan = opath.subpath(0, 2);		// site and feed

		Object oid = db.firstInIndex("dcmFeed", "dcmPath", path);

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

		// retire
		db.setStaticScalar("dcmFeed", oid.toString(), "Retired", true);
	}
}
