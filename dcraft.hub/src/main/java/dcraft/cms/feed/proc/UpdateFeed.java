package dcraft.cms.feed.proc;

import java.time.ZonedDateTime;
import java.util.concurrent.atomic.AtomicReference;

import dcraft.db.DbServiceRequest;
import dcraft.db.proc.IStoredProc;
import dcraft.db.request.query.LoadRecordRequest;
import dcraft.db.request.query.SelectFields;
import dcraft.db.request.update.DbRecordRequest;
import dcraft.db.request.update.InsertRecordRequest;
import dcraft.db.request.update.UpdateRecordRequest;
import dcraft.db.tables.TablesAdapter;
import dcraft.filestore.CommonPath;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
import dcraft.hub.op.OperationOutcomeStruct;
import dcraft.hub.time.BigDateTime;
import dcraft.log.Logger;
import dcraft.service.ServiceHub;
import dcraft.struct.ListStruct;
import dcraft.struct.RecordStruct;
import dcraft.struct.Struct;
import dcraft.struct.builder.ICompositeBuilder;
import dcraft.struct.builder.ObjectBuilder;
import dcraft.util.StringUtil;
import dcraft.util.TimeUtil;

// database does not separate preview from published - fields and tags in general 
// are always published not preview
// however, when no published content is available, we fall back on the preview
public class UpdateFeed implements IStoredProc {
	@Override
	public void execute(DbServiceRequest request, OperationOutcomeStruct callback) throws OperatingContextException {
		RecordStruct params = request.getDataAsRecord();
		
		String localpath = params.getFieldAsString("Path");
		
		String path = "/" + params.getFieldAsString("Site") + "/" +
				params.getFieldAsString("Channel") + localpath;
		
		ListStruct atags = params.getFieldAsList("AuthorizationTags");
		ListStruct tempctags = params.getFieldAsList("ContentTags");
		
		if (tempctags == null)
			tempctags = params.getFieldAsList("PreviewContentTags");
		
		ListStruct tempfields = params.getFieldAsList("Fields");
		
		if (tempfields == null)
			tempfields = params.getFieldAsList("PreviewFields");

		ListStruct ctags = tempctags;
		ListStruct fields = tempfields;
		
		CommonPath cp = CommonPath.from(path);
		
		CommonPath nchan = cp.subpath(0, 2);		// site and channel
		
		// TODO replicating
		// if (task.isReplicating())
		
		TablesAdapter db = TablesAdapter.of(request);
		
		BigDateTime when = BigDateTime.nowDateTime();
		Object oid = db.firstInIndex("dcmFeed", "dcmPath", path, when, false);
		
		AtomicReference<RecordStruct> oldvalues = new AtomicReference<>();
		AtomicReference<ZonedDateTime> updatepub = new AtomicReference<>();
		
		OperationOutcomeStruct fromUpdate = new OperationOutcomeStruct() {
			@Override
			public void callback(Struct result) throws OperatingContextException {
				/*
				 * Update index
				 * 
				 * ^dcmFeedIndex(did, site/channel, publish datetime, id)=[content tags]
				 * 
				 */
				
				OperationContext.getOrThrow().touch();
				
				String recid = null;
				
				if (oid != null) 
					recid = oid.toString();
				else if (result != null)
					recid = ((RecordStruct) result).getFieldAsString("Id");
				
				if (StringUtil.isEmpty(recid)) {
					Logger.error("Unable to update feed index - no id available");
					callback.returnEmpty();
					return;
				}

				String did = request.getTenant();
				
				CommonPath ochan = null;
				ZonedDateTime opubtime = null;
				String otags = "|";
				
				if (oldvalues.get() != null) {
					CommonPath opath  = CommonPath.from(oldvalues.get().getFieldAsString("Path"));
					ochan = opath.subpath(0, 2);		// site and channel
					
					opubtime = oldvalues.get().getFieldAsDateTime("Published");
					
					ListStruct otlist = oldvalues.get().getFieldAsList("ContentTags");
					
					if (ctags != null) 
						otags = "|" + StringUtil.join(otlist.toStringList(), "|") + "|";
				}
				
				ZonedDateTime npubtime = updatepub.get();
				String ntags = "|";
				
				if (npubtime == null)
					npubtime = opubtime;
				
				if (ctags != null) {
					ntags = "|" + StringUtil.join(ctags.toStringList(), "|") + "|";
				}
				else {
					ntags = otags; 
				}
				
				try {
					// only kill if needed
					if ((opubtime != null) && (! opubtime.equals(npubtime) || ! ochan.equals(nchan)))
						request.getInterface().kill(did, "dcmFeedIndex", ochan.toString(), request.getInterface().inverseTime(opubtime), recid);
					
					if (npubtime != null)
						request.getInterface().set(did, "dcmFeedIndex", nchan.toString(), request.getInterface().inverseTime(npubtime), recid, ntags);
				}
				catch (Exception x) {
					Logger.error("Error updating feed index: " + x);
				}

				try {
					ICompositeBuilder out = new ObjectBuilder();
					
					out.startRecord();
					out.field("Id", recid);
					out.endRecord();
					
					callback.returnValue(out.toLocal());
				}
				catch (Exception x) {
					Logger.error("Error writing record id: " + x);
					callback.returnEmpty();
				}
			}
		};
		
		OperationOutcomeStruct fromLoad = new OperationOutcomeStruct() {
			@Override
			public void callback(Struct result) throws OperatingContextException {
				if ((oid != null) && (result == null)) {
					Logger.error("Unable to update feed - id found but no record loaded");
					callback.returnEmpty();
					return;
				}
				
				OperationContext.getOrThrow().touch();
				
				oldvalues.set((RecordStruct) result);
				
				if ((oid == null) && StringUtil.isEmpty(path)) {
					Logger.error("Unable to insert feed - missing Path");
					callback.returnEmpty();
					return;
				}
				
				DbRecordRequest req = (oid == null) ? new InsertRecordRequest() : new UpdateRecordRequest().withId(oid.toString());
				
				req.withTable("dcmFeed");
				
				if (localpath != null)
					req.withUpdateField("dcmLocalPath", localpath);
				
				if (path != null)
					req.withUpdateField("dcmPath", path);
				
				if (atags != null)
					req.withSetList("dcmAuthorizationTags", atags);
				
				if (ctags != null)
					req.withSetList("dcmContentTags", ctags);
				
				if (fields != null) {
					for (int i = 0; i < fields.getSize(); i++) {
						RecordStruct entry = fields.getItemAsRecord(i);
						String key = entry.getFieldAsString("Name") + "." + entry.getFieldAsString("Locale");
						req.withUpdateField("dcmFields", key, entry.getFieldAsString("Value"));
						
						if ("Published".equals(entry.getFieldAsString("Name"))) {
							ZonedDateTime pd = TimeUtil.parseDateTime(entry.getFieldAsString("Value")).withNano(0).withSecond(0);
							updatepub.set(pd);							
							req.withUpdateField("dcmPublished", pd);
						}
						
						if ("AuthorUsername".equals(entry.getFieldAsString("Name"))) {
							Object userid = db.firstInIndex("dcUser", "dcUsername", entry.getFieldAsString("Value"), when, false);
							
							if (userid != null) {
								String uid = userid.toString();
								req.withUpdateField("dcmAuthor", uid, uid);
							}
						}
					}
				}
					
				ServiceHub.call(req.toServiceRequest()
						.withOutcome(fromUpdate)
				);
			}
		};
		
		if (oid != null) {
			ServiceHub.call(new LoadRecordRequest()
					.withTable("dcmFeed")
					.withId(oid.toString())
					.withSelect(new SelectFields()
							.with("Id")
							.with("dcmPath", "Path")
							.with("dcmPublished", "Published")
							.with("dcmContentTags", "ContentTags")
					)
					.toServiceRequest()
					.withOutcome(fromLoad)
			);
		}
		else {
			fromLoad.returnEmpty();
		}
	}
}
