package dcraft.script.inst.doc;

import dcraft.hub.op.OperatingContextException;
import dcraft.script.StackUtil;
import dcraft.script.work.InstructionWork;
import dcraft.struct.BaseStruct;
import dcraft.struct.Struct;
import dcraft.struct.scalar.AnyStruct;
import dcraft.task.IParentAwareWork;
import dcraft.util.StringUtil;
import dcraft.web.ui.inst.W3Closed;
import dcraft.xml.XElement;
import dcraft.xml.XNode;
import dcraft.xml.XText;

import java.util.Map;
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
		boolean expandValues = StackUtil.boolFromSource(state, "ExpandValues");

		if (expandValues && this.hasChildren()) {
			for (int i = 0; i < this.children.size(); i++)
				Out.expandValues(state, this.children.get(i));
		}

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

	static protected String valueMacro(InstructionWork stack, String value) throws OperatingContextException {
		return StackUtil.resolveValueToString(stack, value,true);
	}

	static public void expandValues(InstructionWork stack, XNode node) throws OperatingContextException {
		if (node instanceof XText) {
			String val = ((XText) node).getRawValue();

			val = Out.valueMacro(stack, val);

			((XText) node).setRawValue(val);
		}
		else if (node instanceof XElement) {
			if ((node instanceof Base) && ((Base)node).isExclude())
				return;

			XElement el = (XElement) node;

			// Write the attributes out
			if (el.hasAttributes()) {
				for (Map.Entry<String, String> entry : el.getAttributes().entrySet()) {
					String aname = entry.getKey();

					// note that we do not officially support any special entities in our code
					// except for the XML standards of &amp; &lt; &gt; &quot; &apos;
					// while entities - including &nbsp; - may work in text nodes we aren't supporting
					// them in attributes and suggest the dec/hex codes - &spades; should be &#9824; or &#x2660;
					String normvalue = XNode.unquote(entry.getValue());

					String expandvalue = Out.valueMacro(stack, normvalue);

					el.attr(aname, expandvalue);
				}
			}

			if (el.hasChildren()) {
				for (XNode cnode : el.getChildren())
					Out.expandValues(stack, cnode);
			}
		}
	}
}
