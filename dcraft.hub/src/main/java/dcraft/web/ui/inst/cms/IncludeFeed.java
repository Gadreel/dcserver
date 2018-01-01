package dcraft.web.ui.inst.cms;

import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
import dcraft.log.Logger;
import dcraft.script.ScriptHub;
import dcraft.script.StackUtil;
import dcraft.script.work.InstructionWork;
import dcraft.struct.RecordStruct;
import dcraft.struct.Struct;
import dcraft.struct.scalar.AnyStruct;
import dcraft.util.IOUtil;
import dcraft.script.inst.doc.Base;
import dcraft.script.inst.doc.Out;
import dcraft.util.StringUtil;
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
		el.setName("dc.IncludeFeed");
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
		
		if (Files.exists(cfile)) {
			XElement layout = ScriptHub.parseInstructions(IOUtil.readEntireFile(cfile));
			
			if (layout instanceof Base) {
				Base blayout = (Base) layout;
				
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
				
				if (OperationContext.getOrThrow().getUserContext().isTagged("Admin", "Editor")) {
					this.add(0,
							Band.tag()
								.withClass("dcuiCmsi")
								.withAttribute("Width", "Narrow")
								.withAttribute("Pad", "Small")
								.with(
										Region.tag()
											.attr("Label","CMS - Edit Page Properties")
											.with(EditButton.tag().attr("title", "CMS - edit page properties"))
								)
					);
					
					String realpath = OperationContext.getOrThrow().getSite().relativize(cfile);

					// into /feeds
					realpath = realpath.substring(realpath.indexOf('/', 1));
					
					// into feed folder
					realpath = realpath.substring(realpath.indexOf('/', 1));
					
					this
							.withAttribute("data-cms-editable", "true")
							.withAttribute("data-cms-feed", feed)
							.withAttribute("data-cms-path", realpath);
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
