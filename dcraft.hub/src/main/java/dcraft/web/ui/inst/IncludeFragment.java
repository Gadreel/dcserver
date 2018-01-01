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
import dcraft.struct.Struct;
import dcraft.struct.scalar.AnyStruct;
import dcraft.tenant.WebFindResult;
import dcraft.util.IOUtil;
import dcraft.util.StringUtil;
import dcraft.xml.XElement;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

public class IncludeFragment extends Base {
	static public IncludeFragment tag() {
		IncludeFragment el = new IncludeFragment();
		el.setName("dc.IncludeFragment");
		return el;
	}
	
	@Override
	public XElement newNode() {
		return IncludeFragment.tag();
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
				}
				else {
					Logger.warn("Unable to merge include, root must be an advanced UI tag.");
				}
			}
		}
	}
	
	@Override
	public void renderAfterChildren(InstructionWork state) throws OperatingContextException {
		this.setName("div");
	}
}
