package dcraft.web.ui.inst;

import dcraft.filestore.CommonPath;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
import dcraft.log.Logger;
import dcraft.script.ScriptHub;
import dcraft.script.StackUtil;
import dcraft.script.work.InstructionWork;
import dcraft.script.inst.doc.Base;
import dcraft.script.inst.doc.Out;
import dcraft.struct.RecordStruct;
import dcraft.tenant.WebFindResult;
import dcraft.util.IOUtil;
import dcraft.xml.XElement;

import java.nio.file.Files;
import java.nio.file.Path;

public class IncludeFragmentInline extends Out {
	static public IncludeFragmentInline tag() {
		IncludeFragmentInline el = new IncludeFragmentInline();
		el.setName("dc.IncludeFragmentInline");
		return el;
	}
	
	@Override
	public XElement newNode() {
		return IncludeFragmentInline.tag();
	}
	
	@Override
	public void renderBeforeChildren(InstructionWork state) throws OperatingContextException {
		super.renderBeforeChildren(state);
		
		// remove the children
		this.clearChildren();
		
		RecordStruct page = (RecordStruct) StackUtil.queryVariable(state, "Page");
		
		boolean usemeta = StackUtil.boolFromSource(state, "Meta", false);
		
		// find and include the fragment file
		if (this.hasAttribute("Path")) {
			String tpath = StackUtil.stringFromSource(state, "Path");
			
			CommonPath pp = new CommonPath(tpath + ".html");
			
			WebFindResult ppath = OperationContext.getOrThrow().getSite().webFindFilePath(pp, OperationContext.getOrThrow().getController().getFieldAsRecord("Request").getFieldAsString("View"));
			
			if ((ppath != null) && Files.exists(ppath.file)) {
				XElement layout = ScriptHub.parseInstructions(IOUtil.readEntireFile(ppath.file));
				
				if (layout instanceof Base) {
					Base blayout = (Base) layout;
					
					// TODO merge with meta or not
					
					// pull out the merge parts
					blayout.mergeWithRoot(state, this.getRoot(state), usemeta);
					
					// pull out the UI and copy into us, leave dcuif and Skeleton out
					XElement frag = layout.find("dc.Fragment");
					
					if (frag != null)
						this.replace(frag);
				}
				else {
					Logger.warn("Unable to merge include, root must be an advanced UI tag.");
				}
			}
		}
	}
}