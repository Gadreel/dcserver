package dcraft.script.inst.doc;

import dcraft.hub.op.OperatingContextException;
import dcraft.script.StackUtil;
import dcraft.script.work.InstructionWork;
import dcraft.struct.Struct;
import dcraft.struct.scalar.AnyStruct;
import dcraft.task.IParentAwareWork;
import dcraft.util.StringUtil;
import dcraft.xml.XElement;
import dcraft.xml.XNode;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

public class Template extends Base {
	static public Template tag() {
		Template el = new Template();
		el.setName("dcs.Template");
		return el;
	}
	
	@Override
	public XElement newNode() {
		return Template.tag();
	}

	public Template() {
		this.exclude = true;
	}
	
	@Override
	public void renderBeforeChildren(InstructionWork state) throws OperatingContextException {
		state.getStore().with("Original", this.deepCopy());
		
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
	}
	
	@Override
	public void renderAfterChildren(InstructionWork state) throws OperatingContextException {
		String name = StackUtil.stringFromSource(state,"Name");
		
		if (StringUtil.isNotEmpty(name)) {
			XElement copy = this.newNode();

			if (this.attributes != null) {
				for (Map.Entry<String, String> entry : this.attributes.entrySet()) {
					if (entry.getKey().equals("Name"))
						continue;

					copy.attr(entry.getKey(), entry.getValue());
				}
			}

			if (this.children != null) {
				for (XNode entry : this.children) {
					// don't copy instructions
					if ((entry instanceof XElement) && ((XElement) entry).getName().contains("."))
						continue;

					copy.with(entry.deepCopy());
				}
			}

			StackUtil.addVariable(state.getParent(), name, copy);
		}
		
		// reset so next run is clean (if in a loop)
		this.replace((XElement) state.getStore().getFieldAsComposite("Original"));
	}
}
