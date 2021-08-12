package dcraft.cms.util;

import dcraft.cms.feed.db.FeedUtilDb;
import dcraft.db.BasicRequestContext;
import dcraft.db.tables.TablesAdapter;
import dcraft.filestore.CommonPath;
import dcraft.filestore.FileDescriptor;
import dcraft.filestore.FileStoreFile;
import dcraft.filestore.mem.MemoryStoreFile;
import dcraft.filevault.*;
import dcraft.hub.ResourceHub;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
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
import dcraft.util.cb.CountDownCallback;
import dcraft.web.ui.inst.ICMSAware;
import dcraft.xml.JsonToXml;
import dcraft.xml.XElement;
import dcraft.xml.XNode;
import dcraft.xml.XmlReader;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FeedUtil {
	public enum FieldType {
		Shared,
		Locale,
		Unknown
	}
	
	static public void addFeed(String path, RecordStruct params, OperationOutcomeStruct fcb) throws OperatingContextException {
		Vault feedvault = OperationContext.getOrThrow().getSite().getFeedsVault();
		
		if (feedvault == null) {
			Logger.error("Feeds vault missing.");
			fcb.returnEmpty();
			return;
		}
		
		feedvault.getMappedFileDetail(path, params, new OperationOutcome<FileDescriptor>() {
			@Override
			public void callback(FileDescriptor result) throws OperatingContextException {
				if (this.hasErrors()) {
					fcb.returnEmpty();
					return;
				}
				
				if (this.isEmptyResult()) {
					Logger.error("Your request appears valid but does not map to a folder.  Unable to complete.");
					fcb.returnEmpty();
					return;
				}
				
				FileStoreFile fi = (FileStoreFile) result;
				
				if (fi.exists()) {
					Logger.error("This path would overwrite an existing page. Please edit the page or remove it and then add it.");
					fcb.returnEmpty();
					return;
				}
				
				Vault tempvault = OperationContext.getOrThrow().getSite().getVault("SiteFiles");
				
				if ((tempvault == null) || ! (tempvault instanceof FileStoreVault)) {
					Logger.error("SiteFiles vault missing.");
					fcb.returnEmpty();
					return;
				}
				
				String template = params.getFieldAsString("Template");
				
				if (StringUtil.isEmpty(template)) {
					Logger.error("Missing template name.");
					fcb.returnEmpty();
					return;
				}
				
				// TODO future copy all the feed*.html files over
				String temppath = "/templates/" + fi.getPathAsCommon().getName(0) + "/" + template + "/feed.html";
				
				// only for "pages" feed
				String wwwpath = "/templates/" + fi.getPathAsCommon().getName(0) + "/" + template + "/www.html";
				
				tempvault.getMappedFileDetail(temppath, params,
						new OperationOutcome<FileDescriptor>() {
							@Override
							public void callback(FileDescriptor source) throws OperatingContextException {
								if (this.hasErrors() || this.isEmptyResult()) {
									Logger.error("Template file missing.");
									fcb.returnEmpty();
									return;
								}
								
								((FileStoreFile) source).readAllText(new OperationOutcome<String>() {
									@Override
									public void callback(String result) throws OperatingContextException {
										if (this.hasErrors() || this.isEmptyResult()) {
											Logger.error("Template file is empty.");
											fcb.returnEmpty();
											return;
										}
										
										XElement root = XmlReader.parse(result, false, true);
										
										if (root == null) {
											Logger.error("Template file is not well formed xml.");
											fcb.returnEmpty();
											return;
										}
										
										RecordStruct def = FeedUtil.getFeedDefinition(fi.getPathAsCommon().getName(0));
										
										// feeds use site default
										String defloc = OperationContext.getOrThrow().getSite().getResources().getLocale().getDefaultLocale();
										
										String currloc = params.selectAsString("TrLocale", ResourceHub.getResources().getLocale().getDefaultLocale());
										
										ListStruct fields = params.selectAsList("SetFields");
										
										if (fields != null) {
											for (Struct fld : fields.items()) {
												if (fld instanceof RecordStruct) {
													RecordStruct recfld = (RecordStruct) fld;
													
													FeedUtil.updateField(
															def,
															recfld.getFieldAsString("Name"),
															recfld.getFieldAsString("Value"),
															root,
															currloc,
															defloc
													);
												}
											}
										}
										
										// do pages first so that FeedSearchWork can run in correct order
										OperationOutcomeEmpty feedtransfer = new OperationOutcomeEmpty() {
											@Override
											public void callback() throws OperatingContextException {
												MemoryStoreFile msource = MemoryStoreFile.of(CommonPath.from(temppath))
														.with(root.toPrettyString());
												
												VaultUtil.transfer("Feeds", msource, fi.getPathAsCommon(), null,
														new OperationOutcomeStruct() {
															@Override
															public void callback(Struct result) throws OperatingContextException {
																TablesAdapter adapter = TablesAdapter.of(BasicRequestContext.ofDefaultDatabase());
																
																FeedUtilDb.addHistory(adapter.getRequest().getInterface(), adapter, fi.getPathAsCommon().getName(0), fi.getPathAsCommon().subpath(1).toString(), ListStruct.list()
																		.with(RecordStruct.record()
																				.with("Command", "NoOp")
																		)
																);
																
																fcb.returnEmpty();
															}
														});
											}
										};
										
										CountDownCallback transfercb = new CountDownCallback(1, feedtransfer);
										
										// copy files to www path first, if necessary
										if ("pages".equals(fi.getPathAsCommon().getName(0))) {
											tempvault.getMappedFileDetail(wwwpath,
													params,
													new OperationOutcome<FileDescriptor>() {
														@Override
														public void callback(FileDescriptor wwwsource) throws OperatingContextException {
															if (this.hasErrors() || this.isEmptyResult()) {
																Logger.error("Template web file missing.");
																fcb.returnEmpty();
																return;
															}
															
															CommonPath sitepath = CommonPath.from("/www").resolve(fi.getPathAsCommon().subpath(1));
															
															VaultUtil.transfer("SiteFiles", (FileStoreFile) wwwsource, sitepath, null, new OperationOutcomeStruct() {
																@Override
																public void callback(Struct result) throws OperatingContextException {
																	transfercb.countDown();
																}
															});
														}
													});
										}
										else {
											transfercb.countDown();
										}
									}
								});
							}
						});
			}
		});
	}
	
	static public RecordStruct metaToInfo(String feedname, String currloc, XElement root) throws OperatingContextException {
		// feed definition with shared and locale fields
		RecordStruct def = FeedUtil.getFeedDefinition(feedname);
		
		ListStruct tags = ListStruct.list();
		
		// feeds use site default
		String defloc = OperationContext.getOrThrow().getSite().getResources().getLocale().getDefaultLocale();
		
		if (StringUtil.isEmpty(currloc))
			currloc = ResourceHub.getResources().getLocale().getDefaultLocale();

		Map<String, RecordStruct> fieldmap = new HashMap<>();

		for (XElement meta : root.selectAll("Meta")) {
			String name = meta.getAttribute("Name");
			
			if (StringUtil.isEmpty(name))
				continue;
			
			FieldType type = FeedUtil.getFieldType(def, name);
			
			if (type == FieldType.Shared) {
				fieldmap.put(name, RecordStruct.record()
						.with("Name", name)
						.with("Value", meta.getValue())
				);
			}
			else if (type == FieldType.Locale) {
				fieldmap.put(name, RecordStruct.record()
						.with("Name", name)
						.with("Value", FeedUtil.bestLocaleMatch(meta, currloc, defloc))
				);
			}
		}

		ListStruct fields = ListStruct.list();		// { Name: x, Value: y }

		for (String field : fieldmap.keySet())
			fields.with(fieldmap.get(field));

		for (XElement meta : root.selectAll("Tag")) {
			String value = meta.getAttribute("Value");
			
			if (StringUtil.isEmpty(value))
				continue;
			
			tags.with(value);
		}
		
		return RecordStruct.record()
			.with("Definition", def)
			.with("Fields", fields)
			.with("ContentTags", tags);
	}
	
	static public String bestLocaleMatch(XElement meta, String curloc, String defloc) {
		XElement tel = FeedUtil.bestLocaleMatchEl(meta, curloc, defloc);
		
		if (tel == null)
			return null;

		return tel.getValue();
	}
	
	static public XElement bestLocaleMatchEl(XElement meta, String curloc, String defloc) {
		if (meta == null)
			return null;

		String name = meta.getAttribute("Name");
		
		if (StringUtil.isEmpty(name))
			return null;

		return FeedUtil.bestMatch(meta, curloc, defloc);
	}

	static public XElement bestMatch(XElement parent, String curloc, String defloc) {
		if (parent == null)
			return null;

		XElement bestvalue = null;
		
		for (XNode t : parent.getChildren()) {
			if (! (t instanceof XElement))
				continue;
			
			XElement tel = (XElement) t;
			
			if (! "Tr".equals(tel.getName()))
				continue;
			
			String trl = tel.getAttribute("Locale");
			
			if (StringUtil.isEmpty(trl)) {
				bestvalue = tel;
				continue;
			}
			
			trl = LocaleUtil.normalizeCode(trl);
			
			if (curloc.equals(trl))
				return tel;
			
			if (defloc.equals(trl)) {
				bestvalue = tel;
				continue;
			}
		}
		
		if (bestvalue != null)
			return bestvalue;

		return parent;
	}
	
	static public XElement exactLocaleMatchEl(XElement meta, String curloc, String defloc) {
		String name = meta.getAttribute("Name");
		
		if (StringUtil.isEmpty(name))
			return null;
		
		XElement bestvalue = null;
		
		for (XNode t : meta.getChildren()) {
			if (! (t instanceof XElement))
				continue;
			
			XElement tel = (XElement) t;
			
			if (! "Tr".equals(tel.getName()))
				continue;
			
			String trl = tel.getAttribute("Locale");
			
			if (StringUtil.isEmpty(trl)) {
				bestvalue = tel;
				continue;
			}
			
			trl = LocaleUtil.normalizeCode(trl);
			
			if (curloc.equals(trl))
				return tel;
			
			if (defloc.equals(trl)) {
				bestvalue = tel;
				continue;
			}
		}
		
		if ((bestvalue != null) && (curloc.equals(defloc)))
			return bestvalue;

		return meta;
	}
	
	static public FieldType getFieldType(RecordStruct def, String name) {
		if (StringUtil.isEmpty(name) || (def == null))
			return FieldType.Unknown;
		
		ListStruct localeFields = def.getFieldAsList("LocaleFields");
		
		if (localeFields != null) {
			for (int i = 0; i < localeFields.size(); i++) {
				String fname = localeFields.getItemAsString(i);
				
				if (name.equals(fname))
					return FieldType.Locale;
			}
		}
		
		ListStruct sharedFields = def.getFieldAsList("SharedFields");
		
		if (sharedFields != null) {
			for (int i = 0; i < sharedFields.size(); i++) {
				String fname = sharedFields.getItemAsString(i);
				
				if (name.equals(fname))
					return FieldType.Shared;
			}
		}
		
		return FieldType.Unknown;
	}

	static public void updateField(RecordStruct feedDef, String name, String value, XElement root, String currloc, String defloc) {
		FeedUtil.FieldType ftype = FeedUtil.getFieldType(feedDef, name);

		if (ftype == FeedUtil.FieldType.Shared)
			FeedUtil.updateSharedField(name, value, root);
		else if (ftype == FeedUtil.FieldType.Locale)
			FeedUtil.updateLocaleField(name, value, root, currloc, defloc);
	}
	
	static public void updateSharedField(String name, String value, XElement root) {
		if (StringUtil.isEmpty(name))
			return;
		
		if (StringUtil.isEmpty(value))
			value = "";
		
		for (XElement meta : root.selectAll("Meta")) {
			if (name.equals(meta.getAttribute("Name"))) {
				meta.value(value);
				
				return;
			}
		}
		
		// determine where to add Meta tags into document
		int idxlastmeta = 0;
		
		for (int i = 0; i < root.getChildCount(); i++) {
			XNode node = root.getChild(i);
			
			if ((node instanceof XElement) && "Meta".equals(((XElement) node).getName()))
				idxlastmeta = i;
		}
		
		root.add(idxlastmeta + 1, XElement.tag("Meta")
				.attr("Name", name)
				.value(value)
		);
	}
	
	static public void updateLocaleField(String name, String value, XElement root, String curloc, String defloc) {
		if (StringUtil.isEmpty(name))
			return;
		
		if (StringUtil.isEmpty(value))
			value = "";
		
		for (XElement meta : root.selectAll("Meta")) {
			if (name.equals(meta.getAttribute("Name"))) {
				XElement best = FeedUtil.exactLocaleMatchEl(meta, curloc, defloc);

				// may find Meta if locale matches default
				if (best != null) {
					// best is either a Tr or a Meta, if Meta then clean and add Tr
					if ("Meta".equals(best.getName())) {
						best.removeAttribute("Value");
						
						meta.with(
								XElement.tag("Tr")
										.attr("Locale", curloc)
										.value(value)
						);
					}
					// if Tr update directly
					else {
						best
								.attr("Locale", curloc)
								.value(value);
					}
				}
				// if locale not found then add
				else {
					meta.with(
							XElement.tag("Tr")
									.attr("Locale", curloc)
									.value(value)
					);
				}
				
				return;
			}
		}

		// meta not found, add a new one
		
		// determine where to add Meta tags into document
		int idxlastmeta = 0;
		
		for (int i = 0; i < root.getChildCount(); i++) {
			XNode node = root.getChild(i);
			
			if ((node instanceof XElement) && "Meta".equals(((XElement) node).getName()))
				idxlastmeta = i;
		}
		
		root.add(idxlastmeta + 1, XElement.tag("Meta")
				.attr("Name", name)
				.with(
						XElement.tag("Tr")
								.attr("Locale", curloc)
								.value(value)
				)
		);
	}

	static public String getSharedField(String fldname, XElement root) {
		if(StringUtil.isEmpty(fldname))
			return null;
		
		for (XElement meta : root.selectAll("Meta")) {
			String name = meta.getAttribute("Name");
			
			if (fldname.equals(name))
				return meta.getValue();
		}
		
		return null;
	}

	static public CommonPath translateToWebPath(CommonPath path) {
		XElement def = FeedUtil.getFeedDefinitionRaw(path.getName(0));

		String prefix = def.getAttribute("Path");

		if (StringUtil.isNotEmpty(prefix)) {
			if ("/".equals(prefix))
				path = path.subpath(1);
			else
				path = CommonPath.from(prefix).resolve(path.subpath(1));
		}

		return path;
	}

	static public XElement getFeedDefinitionRaw(String name) {
		List<XElement> defs = ResourceHub.getResources().getConfig().getTagListDeep("Feeds/Definition");

		for (XElement def : defs) {
			if (name.equals(def.getAttribute("Alias"))) {
				return def;
			}
		}

		return null;
	}
	
	static public RecordStruct getFeedDefinition(String name) {
		List<XElement> defs = ResourceHub.getResources().getConfig().getTagListDeep("Feeds/Definition");
		
		RecordStruct ret = FeedUtil.getFeedFields(name, defs);
		
		if (ret == null)
			return null;
		
		for (XElement def : defs) {
			if (name.equals(def.getAttribute("Alias"))) {
				for (Map.Entry<String,String> entry : def.getAttributes().entrySet()) {
					String key = entry.getKey();
					
					if ("LocaleFields".equals(key) || "SharedFields".equals(key) || "RequiredFields".equals(key) || "DesiredFields".equals(key))
						continue;
					
					ret.with(key, entry.getValue());
				}

				ListStruct mapping = ListStruct.list();

				for (XElement map : def.selectAll("FieldMap")) {
					if (map.hasNotEmptyAttribute("Name") && map.hasNotEmptyAttribute("Field")) {
						mapping.with(RecordStruct.record()
								.with("Name", map.attr("Name"))
								.with("Field", map.attr("Field"))
						);
					}
				}

				ret.with("FieldMap", mapping);

				break;
			}
		}
		
		return ret;
	}
	
	static public RecordStruct getFeedFields(String name, List<XElement> defs) {
		for (XElement def : defs) {
			if (name.equals(def.getAttribute("Alias"))) {
				RecordStruct ret = def.hasNotEmptyAttribute("Inherits")
						? FeedUtil.getFeedFields(def.getAttribute("Inherits"), defs)
						: RecordStruct.record();
				
				if (ret != null) {
					if (def.hasNotEmptyAttribute("SharedFields")) {
						ListStruct fields = ret.getFieldAsList("SharedFields");
						
						if (fields == null) {
							fields = ListStruct.list();
							ret.with("SharedFields", fields);
						}
						
						String[] myfields = def.getAttribute("SharedFields").split(",");
						
						for (String fld : myfields) {
							fields.with(fld.trim());
						}
					}
					
					if (def.hasNotEmptyAttribute("LocaleFields")) {
						ListStruct fields = ret.getFieldAsList("LocaleFields");
						
						if (fields == null) {
							fields = ListStruct.list();
							ret.with("LocaleFields", fields);
						}
						
						String[] myfields = def.getAttribute("LocaleFields").split(",");
						
						for (String fld : myfields) {
							fields.with(fld.trim());
						}
					}

					if (def.hasNotEmptyAttribute("DesiredFields")) {
						ListStruct fields = ret.getFieldAsList("DesiredFields");

						if (fields == null) {
							fields = ListStruct.list();
							ret.with("DesiredFields", fields);
						}

						String[] myfields = def.getAttribute("DesiredFields").split(",");

						for (String fld : myfields) {
							fields.with(fld.trim());
						}
					}

					if (def.hasNotEmptyAttribute("RequiredFields")) {
						ListStruct fields = ret.getFieldAsList("RequiredFields");

						if (fields == null) {
							fields = ListStruct.list();
							ret.with("RequiredFields", fields);
						}

						String[] myfields = def.getAttribute("RequiredFields").split(",");

						for (String fld : myfields) {
							fields.with(fld.trim());
						}
					}
				}
				
				return ret;
			}
		}
		
		return null;
	}

	static public void reindexFeedFile(CommonPath path) throws  OperatingContextException {
		IndexTransaction tx = IndexTransaction.of(OperationContext.getOrThrow().getSite().getFeedsVault());
		tx.withUpdate(path);
		tx.commit();
	}

	/*
		command is reduced version of the Feeds vault custom command, only needs/looks at this part
		
		{
			Command: 'SavePart',
			Params: {
				PartId: 'nnn',
				Part: widget json from xml
			}
		}
	 */
	
	static public void applyCommand(CommonPath path, XElement root, RecordStruct command, boolean old) throws OperatingContextException {
		if (root == null) {
			Logger.error("Feed file not well formed XML");
			return;
		}
		
		String cmd = command.getFieldAsString("Command");
		
		String partid = command.selectAsString("Params.PartId");
		XElement part = null;
		
		// is this is for a part (not all commands are)
		if (StringUtil.isNotEmpty(partid)) {
			part = root.findId(partid);		// TODO set stack levels
			
			if (part == null) {
				Logger.error("Feed file missing part");
				return;
			}
			
			// TODO check if user can dit it
			
			// part may apply the command itself and that is the end
			if (! old && (part instanceof ICMSAware)) {
				if (((ICMSAware) part).applyCommand(path, root, command))
					return;
			}
		}
		
		if ("SavePart".equals(cmd) && ! old) {
			if (part == null) {
				Logger.error("Feed file missing part");
				return;
			}
			
			// TODO check to see if authorized to edit this part
			// using a new security method on the UI element
			
			XElement newel = ScriptHub.parseInstructions(command.selectAsString("Params.Part"));
			
			if (newel == null) {
				Logger.error("New part content is not valid xml, cannot apply");
				return;
			}
			
			// TODO check that the changes made are allowed - e.g. on TextWidget
			// an Editor cannot change to Unsafe mode
			
			part.replace(newel);
			
			return;
		}
		else if ("SavePart".equals(cmd) && old) {
			if (part == null) {
				Logger.error("Feed file missing part");
				return;
			}

			// TODO check to see if authorized to edit this part
			// using a new security method on the UI element

			String partpath = command.selectAsString("Params.PartPath", "=");

			if (!("=".equals(partpath) || "_".equals(partpath))) {
				part = part.selectFirst(partpath);
			}

			if (part == null) {
				Logger.error("Feed file missing part path");
				return;
			}

			String loadmode = command.selectAsString("Params.Mode", "json");
			XElement newel = null;

			if ("json".equals(loadmode)) {
				RecordStruct newpart = null;

				if ("_".equals(partpath)) {
					ListStruct extra = command.selectAsList("Params.Part");

					newpart = RecordStruct.record()
							.with("type", "element")
							.with("name", "dummy")
							.with("children", extra);
				}
				else {
					newpart = command.selectAsRecord("Params.Part");
				}

				if (newpart == null) {
					Logger.error("Missing new part content");
					return;
				}

				newel = JsonToXml.convertJson(newpart);
			}
			else if ("text".equals(loadmode)) {
				String newpart = command.selectAsString("Params.Part");

				if (newpart == null) {
					Logger.error("Missing new part content");
					return;
				}


				if ("_".equals(partpath)) {
					newpart = "<dummy>" + newpart + "</dummy>";
				}

				newel = ScriptHub.parseInstructions(newpart);
			}
			else {
				Logger.error("Invalid save mode");
				return;
			}

			if (newel == null) {
				Logger.error("New part content is not valid xml");
				return;
			}

			// TODO check that the changes made are allowed - e.g. on TextWidget
			// an Editor cannot change to Unsafe mode

			if ("_".equals(partpath)) {
				part.replaceChildren(newel);
			} else {
				part.replace(newel);
			}
			
			return;
		}
		else if ("Reorder".equals(cmd)) {
			if (part == null) {
				Logger.error("Feed file missing part");
				return;
			}
			
			// TODO check to see if authorized to edit this part
			// using a new security method on the UI element
			
			ListStruct neworder = command.selectAsList("Params.Order");
			
			if (neworder == null) {
				Logger.error("New order is missing");
				return;
			}
			
			List<XNode> children = part.getChildren();
			
			part.clearChildren();
			
			for (int i = 0; i < neworder.size(); i++) {
				int pos = neworder.getItemAsInteger(i).intValue();
				
				part.with(children.get(pos));
			}
			
			return;
		}
		else if ("SaveMeta".equals(cmd)) {
			RecordStruct def = FeedUtil.getFeedDefinition(path.getName(1));
			
			// feeds use site default
			String defloc = OperationContext.getOrThrow().getSite().getResources().getLocale().getDefaultLocale();
			
			String currloc = command.selectAsString("Params.TrLocale", ResourceHub.getResources().getLocale().getDefaultLocale());
			
			ListStruct metalist = command.selectAsList("Params.SetFields");
			
			if (metalist != null) {
				for (int i = 0; i < metalist.size(); i++) {
					RecordStruct newmeta = metalist.getItemAsRecord(i);
					
					FeedUtil.updateField(
							def,
							newmeta.getFieldAsString("Name"),
							newmeta.getFieldAsString("Value"),
							root,
							currloc,
							defloc
					);
				}
			}
			
			return;
		}
		else if ("SaveTags".equals(cmd)) {
			// clear tags
			for (XElement tag : root.selectAll("Tag")) {
				root.remove(tag);
			}
			
			ListStruct taglist = command.selectAsList("Params.SetTags");
			
			if (taglist != null) {
				for (int i = 0; i < taglist.size(); i++) {
					String newmeta = taglist.getItemAsString(i);
					
					root.with(
							XElement.tag("Tag")
								.attr("Value", newmeta)
					);
				}
			}
			
			return;
		}
		else if ("NoOp".equals(cmd)) {
			// ignore - no operation
			return;
		}
		
		Logger.warn("Unrecognized command: " + cmd);
	}
}
