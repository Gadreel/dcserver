package dcraft.web.ui.inst.cms;

import dcraft.cms.feed.db.HistoryFilter;
import dcraft.cms.util.FeedUtil;
import dcraft.db.DbServiceRequest;
import dcraft.db.proc.BasicFilter;
import dcraft.db.proc.ExpressionResult;
import dcraft.db.proc.IFilter;
import dcraft.db.proc.filter.CurrentRecord;
import dcraft.db.proc.filter.Unique;
import dcraft.db.tables.TablesAdapter;
import dcraft.db.util.DbUtil;
import dcraft.filestore.CommonPath;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
import dcraft.hub.op.OperationMarker;
import dcraft.log.Logger;
import dcraft.script.ScriptHub;
import dcraft.script.StackUtil;
import dcraft.script.work.InstructionWork;
import dcraft.struct.ListStruct;
import dcraft.struct.RecordStruct;
import dcraft.struct.Struct;
import dcraft.struct.scalar.AnyStruct;
import dcraft.struct.scalar.BooleanStruct;
import dcraft.struct.scalar.StringStruct;
import dcraft.task.IParentAwareWork;
import dcraft.util.IOUtil;
import dcraft.script.inst.doc.Base;
import dcraft.script.inst.doc.Out;
import dcraft.util.StringUtil;
import dcraft.web.ui.UIUtil;
import dcraft.web.ui.inst.Band;
import dcraft.web.ui.inst.Region;
import dcraft.web.ui.inst.W3;
import dcraft.xml.XElement;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

public class IncludeFeed extends Base {
	static public IncludeFeed tag() {
		IncludeFeed el = new IncludeFeed();
		el.setName("dcm.IncludeFeed");
		return el;
	}
	
	@Override
	public XElement newNode() {
		return IncludeFeed.tag();
	}
	
	@Override
	public void renderBeforeChildren(InstructionWork state) throws OperatingContextException {
		// setup params
		for (XElement pel : this.selectAll("Param")) {
			String name = StackUtil.stringFromElement(state, pel, "Name");
			
			if (StringUtil.isNotEmpty(name)) {
				Struct val = StackUtil.refFromElement(state, pel, "Value");
				
				if (val != null) {
					StackUtil.addVariable(state, name, val);
				}
				else if (this.hasChildren()) {
					///XElement copy =  .deepCopy(); not needed see Original above
					
					StackUtil.addVariable(state, name, AnyStruct.of(pel));
				}
			}
		}
		
		// remove the children
		this.clearChildren();
		
		RecordStruct page = (RecordStruct) StackUtil.queryVariable(state, "Page");
		
		boolean usemeta = StackUtil.boolFromSource(state, "Meta", false);
		String feed = StackUtil.stringFromSource(state, "Name", "pages");
		String path = StackUtil.stringFromSource(state, "Path", page.getFieldAsString("Path"));

		String fpath = "/" + feed + path;

		if (! fpath.endsWith(".html"))
			fpath = fpath + ".html";

		Path cfile = OperationContext.getOrThrow().getSite().findSectionFile("feeds", fpath, OperationContext.getOrThrow().getController().getFieldAsRecord("Request").getFieldAsString("View"));
		
		if ((cfile != null) && Files.exists(cfile)) {
			XElement layout = ScriptHub.parseInstructions(IOUtil.readEntireFile(cfile));
			
			if (layout instanceof Base) {
				Base blayout = (Base) layout;
				
				// TODO refine when this is possible - include feed inline also
				// if in edit mode, apply draft/schedule
				if (OperationContext.getOrThrow().getUserContext().isTagged("Admin", "Editor")) {
					String cmsmode = Struct.objectToString(StackUtil.queryVariable(null, "_Controller.Request.Cookies.dcmMode"));
					
					if (StringUtil.isEmpty(cmsmode))
						cmsmode = "now";
					
					// TODO add support for dates as well - look into future for schedules when selected

					if (cmsmode.equals("now")) {
						CommonPath epath = CommonPath.from("/" + OperationContext.getOrThrow().getSite().getAlias() + fpath.substring(0, fpath.length() - 5));
						
						// may reconsider later
						DbServiceRequest request = DbUtil.fakeRequest();
						
						TablesAdapter db = TablesAdapter.ofNow(request);
						
						
						Unique collector = (Unique) db.traverseIndex(OperationContext.getOrThrow(), "dcmFeedHistory", "dcmDraftPath", epath.toString(), Unique.unique().withNested(
								CurrentRecord.current().withNested(HistoryFilter.forDraft())));
						
						String hid = collector.isEmpty() ? null : collector.getOne().toString();
						
						if (StringUtil.isNotEmpty(hid)) {
							// there should only be one "accepted" entry, that would be an "edit mode" entry (no schedule)
							
							for (String key : db.getStaticListKeys("dcmFeedHistory", hid, "dcmModifications")) {
								RecordStruct command = Struct.objectToRecord(db.getStaticList("dcmFeedHistory", hid, "dcmModifications", key));
								
								// check null, modification could be retired
								if (command != null) {
									try (OperationMarker om = OperationMarker.create()) {
										FeedUtil.applyCommand(epath, layout, command, false);
										
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
							
							IncludeFeed.this.withAttribute("data-cms-draft", "true");
						}
					}
				}
				
				// TODO merge with meta or not
				
				// pull out the merge parts
				blayout.mergeWithRoot(state, this.getRoot(state), usemeta);
				
				// pull out the UI and copy into us, leave dcuif and Skeleton out
				XElement frag = layout.find("dc.Fragment");
				
				if (frag != null) {
					if (frag.hasAttributes()) {
						for (Map.Entry<String, String> attr : frag.getAttributes().entrySet())
							if (! this.hasAttribute(attr.getKey()) && ! "class".equals(attr.getKey()))
								this.setAttribute(attr.getKey(), attr.getValue());
					}
					
					// copy over the page class
					String pclass = StackUtil.stringFromElement(state, frag,"class");
					
					if (StringUtil.isNotEmpty(pclass))
						this.withClass(pclass);
					
					// copy appropriate children over
					for (XElement el : frag.selectAll("*")) {
						this.add(el);
					}
				}
				
				/*
				if (usemeta) {
					page
							.with("CmsFeed", feed)
							.with("CmsPath", path);
				}
				*/
				
				// TODO refine when this is possible - inline also
				if (OperationContext.getOrThrow().getUserContext().isTagged("Admin", "Editor")) {
					String realpath = OperationContext.getOrThrow().getSite().relativize(cfile);

					// into /feeds
					realpath = realpath.substring(realpath.indexOf('/', 1));
					
					// into feed folder
					realpath = realpath.substring(realpath.indexOf('/', 1));
					
					this
							.withAttribute("data-cms-type", "feed")
							.withAttribute("data-cms-meta", usemeta ? "true" : "false")
							.withAttribute("data-cms-feed", feed)
							.withAttribute("data-cms-path", realpath);

					// if any badges indicated for this feed
					UIUtil.setEditBadges(state, this);

					// indicates only that editing is possible, badges still are checked
					StackUtil.addVariable(state, "_CMSEditable", BooleanStruct.of(true));
				}
			}
			else {
				Logger.warn("Unable to merge feed, root must be an advanced UI tag.");
			}
		}
		else {
			// TODO broken widget
			Logger.warn("Unable to merge feed, feed not found must be an advanced UI tag.");
		}
	}
	
	@Override
	public void renderAfterChildren(InstructionWork state) throws OperatingContextException {
		this
				.withClass("dc-widget", "dc-widget-image")
				.withAttribute("data-dc-enhance", "true")
				.withAttribute("data-dc-tag", this.getName());
		
		this.setName("div");
	}
}
