package dcraft.web.ui.inst;

import dcraft.filestore.CommonPath;
import dcraft.hub.op.IVariableAware;
import dcraft.hub.op.IVariableProvider;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
import dcraft.log.Logger;
import dcraft.script.ScriptHub;
import dcraft.script.StackUtil;
import dcraft.script.work.BlockWork;
import dcraft.script.work.InstructionWork;
import dcraft.script.inst.doc.Base;
import dcraft.script.inst.doc.Out;
import dcraft.struct.FieldStruct;
import dcraft.struct.RecordStruct;
import dcraft.struct.scalar.BooleanStruct;
import dcraft.struct.scalar.StringStruct;
import dcraft.tenant.WebFindResult;
import dcraft.util.IOUtil;
import dcraft.web.ui.UIUtil;
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
			
			if ((ppath != null) && (ppath.file != null) && Files.exists(ppath.file)) {
				XElement layout = ScriptHub.parseInstructions(IOUtil.readEntireFile(ppath.file));
				
				if (layout instanceof Base) {
					Base blayout = (Base) layout;

					StackUtil.addVariable(state, "_CMSFile", StringStruct.of(ppath.file.toString()));
					StackUtil.addVariable(state, "_CMSFeedName", null);
					StackUtil.addVariable(state, "_CMSFeedPath", null);
					//StackUtil.addVariable(state, "_CMSEditable", BooleanStruct.of(false));

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
	
	@Override
	public void renderAfterChildren(InstructionWork state) throws OperatingContextException {
		super.renderAfterChildren(state);
		
		// migrate my vars up
		if (state instanceof BlockWork) {
			IVariableProvider src = (IVariableProvider) state;
			
			IVariableProvider dest = StackUtil.queryVarProvider(state.getParent());
			
			if ((src != null) && (dest != null)) {
				RecordStruct vars = src.variables();
				
				if (vars != null) {
					for (FieldStruct fld : vars.getFields()) {
						dest.addVariable(fld.getName(), fld.getValue());
					}
				}
			}
		}
	}
}