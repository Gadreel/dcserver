package dcraft.script.inst.doc;

import dcraft.hub.ResourceHub;
import dcraft.hub.op.OperatingContextException;
import dcraft.log.Logger;
import dcraft.script.StackUtil;
import dcraft.script.inst.BlockInstruction;
import dcraft.script.inst.For;
import dcraft.script.work.ExecuteState;
import dcraft.script.work.InstructionWork;
import dcraft.script.work.OperationsWork;
import dcraft.script.work.ReturnOption;
import dcraft.struct.ScalarStruct;
import dcraft.struct.Struct;
import dcraft.struct.scalar.AnyStruct;
import dcraft.task.IParentAwareWork;
import dcraft.util.StringUtil;
import dcraft.web.ui.inst.Html;
import dcraft.web.ui.inst.W3;
import dcraft.web.ui.inst.form.Form;
import dcraft.xml.XElement;
import dcraft.xml.XNode;
import dcraft.xml.XText;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class Base extends BlockInstruction {
	static public Base tag(String name) {
		Base el = new Base();
		el.setName(name);
		return el;
	}
	
	protected boolean exclude = false;
	
	public boolean isExclude() {
		return this.exclude;
	}

	public void setExclude(boolean v) {
		this.exclude = v;
	}
	
	public Base() {
	}
	
	public Base(String tag) {
		this.setName(tag);
	}
	
	@Override
	public XElement newNode() {
		return Base.tag(this.tagName);
	}
	
	public void renderBeforeChildren(InstructionWork state) throws OperatingContextException {
	}
	
	public void renderAfterChildren(InstructionWork state) throws OperatingContextException {
	}
	
	public void cleanup(InstructionWork state) throws OperatingContextException {
	}
	
	@Override
	public ReturnOption run(InstructionWork state) throws OperatingContextException {
		if (state.getState() == ExecuteState.READY) {
			this.renderBeforeChildren(state);
			
			if (this.gotoTop(state))
				return ReturnOption.CONTINUE;
		}
		else {
			// each resume should go to next child instruction until we run out
			if (this.gotoNext(state, false))
				return ReturnOption.CONTINUE;
		}
		
		// done, cleanup
		this.cleanup(state);
		
		if (this.children != null) {
			List<XNode> newchildren = new ArrayList<>();
			
			for (int i = 0 ; i < this.children.size(); i++) {
				XNode node = this.children.get(i);
				
				if (node instanceof Base) {
					newchildren.add(node);
				}
				else if (node instanceof XText) {
					XText tnode = (XText) node;
					
					String val = tnode.getRawValue();
					
					if (StringUtil.isNotEmpty(val)) {
						tnode.setRawValue(StackUtil.resolveValueToString(state, val), tnode.isCData());
						newchildren.add(tnode);
					}
				}
			}
			
			this.children = newchildren;
		}

		if (this.attributes != null) {
			//if ("img".equals(this.tagName))
			//	System.out.println("img");
			
			for (Map.Entry<String,String> entry : this.attributes.entrySet()) {
				String val = XNode.unquote(entry.getValue());

				if (StringUtil.isNotEmpty(val)) {
					entry.setValue(XNode.quote(StackUtil.resolveValueToString(state, val)));
				}
			}
		}

		this.renderAfterChildren(state);
		
		if (! (this instanceof Template)) {
			String name = StackUtil.stringFromSource(state, "Name");
			
			if (StringUtil.isNotEmpty(name)) {
				StackUtil.addVariable(state.getParent(), name, this);
				
				// TODO switch target? -- ((OperationsWork) state).setTarget(var);
				
				// if this is a regular html/xml tag then remove extra attribute
				if (Character.isLowerCase(this.tagName.charAt(0)) && !this.tagName.contains("."))
					this.removeAttribute("Name");
			}
		}
		
		return ReturnOption.DONE;
	}
	
	public Base withClass(String... classnames) {
		for (String cname : classnames) {
			if (StringUtil.isNotEmpty(cname)) {
				String cv = this.getAttribute("class");
				
				if (StringUtil.isNotEmpty(cv)) {
					// TODO check for duplicates - must match entire name not just part, in the cv
					this.setAttribute("class", cv + " " + cname);
				}
				else {
					this.setAttribute("class", cname);
				}
			}
		}
		
		return this;
	}
	
	// do more tests on this - TODO make more efficient
	public Base withoutClass(String... classnames) {
		for (String cname : classnames) {
			if (StringUtil.isNotEmpty(cname)) {
				String currclass = this.getAttribute("class");
				
				// TODO repeat loop until class is thoroughly gone
				if (StringUtil.isNotEmpty(currclass)) {
					int pos = currclass.indexOf(cname);
					
					if (pos == 0) {
						String newclass = currclass.substring(pos + cname.length());
						this.setAttribute("class", newclass);
					}
					else if (pos > 0) {
						String newclass = currclass.substring(0, pos)
								+ currclass.substring(pos + cname.length());
						this.setAttribute("class", newclass);
					}
				}
			}
		}
		
		return this;
	}
	
	// do more tests on this - TODO make more efficient
	public boolean hasClass(String cname) {
		if (StringUtil.isNotEmpty(cname)) {
			String currclass = this.getAttribute("class");
			
			// TODO check for space or end to make sure it is a pure match
			if (StringUtil.isNotEmpty(currclass)) {
				return (currclass.indexOf(cname) != -1);
			}
		}
		
		return false;
	}
	
	// get a UI element above me, might skip instructions
	public Base getParent(InstructionWork state) {
		IParentAwareWork pw = state.getParent();
		
		while (pw != null) {
			if (pw instanceof InstructionWork) {
				XElement pel = ((InstructionWork) pw).getInstruction();
				
				if (pel instanceof Base)
					return (Base) pel;
			}
			
			pw = pw.getParent();
		}
		
		return null;
	}

	// get top level UI element
	public Base getRoot(InstructionWork state) {
		IParentAwareWork pw = state.getParent();

		while (pw != null) {
			if (pw instanceof InstructionWork) {
				XElement pel = ((InstructionWork) pw).getInstruction();

				if (pel instanceof Base)
					return ((Base) pel).getRoot((InstructionWork) pw);
			}

			pw = pw.getParent();
		}

		return this;
	}

	// if this block is a fragment that should be merged with root, call this during build
	// PagePart, PagePartDef, Skeleton (only first is used), Function, Require
	public void mergeWithRoot(InstructionWork state, Base root, boolean usemeta) throws OperatingContextException {
		// copy all attributes over, unless they have been overridden
		// TODO try to expand the elements from stack as they may contain params
		if (this.attributes != null) {
			for (Map.Entry<String, String> attr : this.attributes.entrySet())
				if (! root.hasAttribute(attr.getKey()) && ! "PageClass".equals(attr.getKey()))
					root.setAttribute(attr.getKey(), attr.getValue());
		}
	
		// copy over the page class
		String pclass = StackUtil.stringFromElement(state, this,"PageClass");
		
		if (StringUtil.isNotEmpty(pclass))
			root.withAttribute("PageClass", root.getAttribute("PageClass", "") + " " + pclass);
		
		// copy appropriate children over
		for (XElement el : this.selectAll("*")) {
			String tname = el.getName();
			
			if ("Function".equals(tname) || "Require".equals(tname))
				root.add(el);
			
			// TODO review - usemeta may not even do anything, see below - perhaps remove
			if (usemeta && "Meta".equals(tname))
				root.add(el);

			if (usemeta && "meta".equals(tname))
				root.add(el);
		}

		// meta becomes a variable always
		Html.mergePageVariables(state, this);
	}
	
	public Form getForm(InstructionWork state) {
		IParentAwareWork pw = state.getParent();
		
		while (pw != null) {
			if (pw instanceof InstructionWork) {
				XElement pel = ((InstructionWork) pw).getInstruction();
				
				if (pel instanceof Base)
					return ((Base) pel).getForm((InstructionWork) pw);
			}
			
			pw = pw.getParent();
		}
		
		return null;
	}
}
