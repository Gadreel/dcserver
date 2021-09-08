package dcraft.web.ui.inst;

import dcraft.filestore.CommonPath;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
import dcraft.log.Logger;
import dcraft.script.ScriptHub;
import dcraft.script.StackUtil;
import dcraft.script.inst.doc.Base;
import dcraft.script.work.InstructionWork;
import dcraft.struct.Struct;
import dcraft.struct.scalar.AnyStruct;
import dcraft.struct.scalar.BooleanStruct;
import dcraft.struct.scalar.StringStruct;
import dcraft.task.IParentAwareWork;
import dcraft.tenant.WebFindResult;
import dcraft.util.IOUtil;
import dcraft.util.StringUtil;
import dcraft.xml.XElement;
import dcraft.xml.XNode;

import java.nio.file.Files;
import java.util.Collection;
import java.util.Map;

public class IncludeDynamic extends Base {
	static public IncludeDynamic tag() {
		IncludeDynamic el = new IncludeDynamic();
		el.setName("dc.IncludeDynamic");
		return el;
	}
	
	@Override
	public XElement newNode() {
		return IncludeDynamic.tag();
	}
	
	@Override
	public void renderBeforeChildren(InstructionWork state) throws OperatingContextException {
		// setup params
		// TODO params concept is not tested here, just copied
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

		// find and include the fragment file
		if (this.hasAttribute("Path")) {
			String tpath = StackUtil.stringFromSource(state, "Path");

			CommonPath pp = new CommonPath(tpath + ".dcs.xml");

			WebFindResult ppath = OperationContext.getOrThrow().getSite().webFindFilePath(pp, OperationContext.getOrThrow().getController().getFieldAsRecord("Request").getFieldAsString("View"));
			
			if ((ppath != null) && (ppath.file != null) && Files.exists(ppath.file)) {
				XElement layout = ScriptHub.parseInstructions(IOUtil.readEntireFile(ppath.file));

				if (layout != null) {
					Collection<XNode> children = layout.getChildren();

					for (XNode node : children) {
						this.with(node);
					}
				}
				else {
					Logger.warn("Unable to include dynamic, missing or malformed xml.");
				}
			}
		}
	}
	
	@Override
	public void renderAfterChildren(InstructionWork state) throws OperatingContextException {
		this.withClass("dc-dynamic-area")
				.withAttribute("data-dc-path", StackUtil.stringFromSource(state, "Path"))
				.withAttribute("data-dc-enhance", "true")
				.withAttribute("data-dc-tag", this.getName());

		this.setName("div");
	}
}
