package dcraft.mail.dcc.inst;

import dcraft.filestore.CommonPath;
import dcraft.hub.ResourceHub;
import dcraft.hub.op.IVariableProvider;
import dcraft.hub.op.OperatingContextException;
import dcraft.log.Logger;
import dcraft.script.ScriptHub;
import dcraft.script.StackUtil;
import dcraft.script.inst.doc.Base;
import dcraft.script.inst.doc.Out;
import dcraft.script.work.BlockWork;
import dcraft.script.work.InstructionWork;
import dcraft.struct.FieldStruct;
import dcraft.struct.RecordStruct;
import dcraft.util.IOUtil;
import dcraft.xml.XElement;

import java.nio.file.Path;

public class IncludeFragmentInline extends Out {
	static public IncludeFragmentInline tag() {
		IncludeFragmentInline el = new IncludeFragmentInline();
		el.setName("dcc.IncludeFragmentInline");
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

		// find and include the fragment file
		if (this.hasAttribute("Path")) {
			String tpath = StackUtil.stringFromSource(state, "Path");
			
			CommonPath pp = new CommonPath(tpath + ".dcc.xml");

			Path ppath = ResourceHub.getResources().getComm().findCommFile(pp);

			if (ppath != null) {
				XElement layout = ScriptHub.parseInstructions(IOUtil.readEntireFile(ppath));
				
				if (layout instanceof Base) {
					Base blayout = (Base) layout;

					// pull out the merge parts
					blayout.mergeWithRoot(state, this.getRoot(state), false);
					
					// pull out the UI and copy into us, leave dcuif and Skeleton out
					XElement frag = layout.find("dcc.Fragment");
					
					if (frag != null) {
						this.replace(frag);

						for (XElement func : layout.selectAll("dcs.Function"))
							this.add(0, func);
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