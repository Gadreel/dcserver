package dcraft.web.ui.inst.cms;

import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
import dcraft.log.Logger;
import dcraft.script.ScriptHub;
import dcraft.script.StackUtil;
import dcraft.script.work.InstructionWork;
import dcraft.struct.RecordStruct;
import dcraft.util.IOUtil;
import dcraft.script.inst.doc.Base;
import dcraft.script.inst.doc.Out;
import dcraft.xml.XElement;

import java.nio.file.Files;
import java.nio.file.Path;

public class IncludeFeedInline extends Out {
	static public IncludeFeedInline tag() {
		IncludeFeedInline el = new IncludeFeedInline();
		el.setName("dc.IncludeFeedInline");
		return el;
	}
	
	@Override
	public XElement newNode() {
		return IncludeFeedInline.tag();
	}
	
	@Override
	public void renderBeforeChildren(InstructionWork state) throws OperatingContextException {
		super.renderBeforeChildren(state);
		
		// remove the children
		this.clearChildren();
		
		RecordStruct page = (RecordStruct) StackUtil.queryVariable(state, "Page");
		
		boolean usemeta = StackUtil.boolFromSource(state, "Meta", false);
		String feed = StackUtil.stringFromSource(state, "Name", "pages");
		String path = StackUtil.stringFromSource(state, "Path", page.getFieldAsString("Path"));
		
		Path cfile = OperationContext.getOrThrow().getSite().findSectionFile("feeds", "/" + feed + path + ".html", OperationContext.getOrThrow().getController().getFieldAsRecord("Request").getFieldAsString("View"));
		
		if (Files.exists(cfile)) {
			XElement layout = ScriptHub.parseInstructions(IOUtil.readEntireFile(cfile));
			
			if (layout instanceof Base) {
				Base blayout = (Base) layout;
				
				// TODO merge with meta or not
				
				// pull out the merge parts
				blayout.mergeWithRoot(state, this.getRoot(state), usemeta);
				
				// pull out the UI and copy into us, leave dcuif and Skeleton out
				XElement frag = layout.find("dc.Fragment");
				
				if (frag != null)
					this.replace(frag);
				
				/*
				if (usemeta) {
					page
							.with("CmsFeed", feed)
							.with("CmsPath", path);
				}
				*/
				
				if (OperationContext.getOrThrow().getUserContext().isTagged("Admin", "Editor")) {
					/* TODO
					this.with(
							EditButton.tag()
					);
					*/
					
					this
							.withAttribute("data-cms-feed", feed)
							.withAttribute("data-cms-path", path);
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
}