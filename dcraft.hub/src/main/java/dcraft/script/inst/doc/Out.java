package dcraft.script.inst.doc;

import dcraft.hub.op.OperatingContextException;
import dcraft.script.StackUtil;
import dcraft.script.work.InstructionWork;
import dcraft.struct.BaseStruct;
import dcraft.struct.Struct;
import dcraft.struct.scalar.AnyStruct;
import dcraft.task.IParentAwareWork;
import dcraft.util.StringUtil;
import dcraft.xml.XElement;

import java.util.concurrent.CopyOnWriteArrayList;

public class Out extends Base {
	static public Out tag() {
		Out el = new Out();
		el.setName("dcs.Out");
		return el;
	}
	
	@Override
	public XElement newNode() {
		return Out.tag();
	}

	public Out() {
		this.exclude = true;
	}
	
	@Override
	public void renderBeforeChildren(InstructionWork state) throws OperatingContextException {
		state.getStore().with("Original", this.deepCopy());
		
		// setup params
		for (XElement pel : this.selectAll("Param")) {
			String name = StackUtil.stringFromElement(state, pel, "Name");
			
			if (StringUtil.isNotEmpty(name)) {
				BaseStruct val = StackUtil.refFromElement(state, pel, "Value");
				
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
		// copy current content up into the document
		String forid = StackUtil.stringFromSource(state,"For");
		//String name = StackUtil.stringFromSource(state,"Name");
		
		if (StringUtil.isNotEmpty(forid)) {
			Base root = this.getRoot(state);

			if (root == this) {
				// if is null - nothing I think, this could happen in an include script
			}
			else {
				XElement pel = "Top".equals(forid) ? root : root.findId(forid);

				if (pel != null) {
					if (this.children != null)
						for (int i = 0; i < this.children.size(); i++)
							pel.add(this.children.get(i));
				}
				else {
					// TODO what if is null
				}
			}
		}
		/*
		else if (StringUtil.isNotEmpty(name)) {
			//XElement template = this.deepCopy();
			
			// so that Base doesn't make a VAR from this
			//template.removeAttribute("Name");
			
			System.out.println("dfsfddf");
			
			//Struct var = AnyStruct.of(this);
			
			//StackUtil.addVariable(state.getParent(), name, var);
		}
		*/
		else {
			IParentAwareWork pw = state.getParent();
			XElement posel = this;
			
			while (pw != null) {
				if (pw instanceof InstructionWork) {
					XElement pel = ((InstructionWork) pw).getInstruction();
					
					if (pel instanceof Base) {
						int pos = pel.findIndex(posel);
						
						if (this.children == null)
							this.children = new CopyOnWriteArrayList<>();
						
						for (int i = 0; i < this.children.size(); i++)
							pel.add(pos + i, this.children.get(i));
						
						break;
					}
					else {
						posel = pel;
					}
				}
				
				pw = pw.getParent();
			}
		}
		
		// reset so next run is clean (if in a loop)
		this.replace((XElement) state.getStore().getFieldAsComposite("Original"));
		
		//ystem.out.println("> " + this.hashCode());
	}
}
