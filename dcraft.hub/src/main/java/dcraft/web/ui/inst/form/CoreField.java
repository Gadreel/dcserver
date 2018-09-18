package dcraft.web.ui.inst.form;

import java.util.concurrent.CopyOnWriteArrayList;

import dcraft.hub.op.OperatingContextException;
import dcraft.log.Logger;
import dcraft.script.StackUtil;
import dcraft.script.work.InstructionWork;
import dcraft.struct.Struct;
import dcraft.struct.scalar.AnyStruct;
import dcraft.util.RndUtil;
import dcraft.web.ui.UIUtil;
import dcraft.script.inst.doc.Base;
import dcraft.web.ui.inst.W3;
import dcraft.xml.XElement;
import dcraft.xml.XNode;

abstract public class CoreField extends Base {
	protected String fieldid = null;
	protected Base fieldinfo = null;
	
	abstract public void addControl(InstructionWork state) throws OperatingContextException;
	
	/**
	 * call this from subclasses before making child updates in
	 * expand
	 * 
	 * no field children are directly used in the final document, 
	 * move them out and make room for the real field children
	 */
	public void initFieldInfo(InstructionWork state) throws OperatingContextException {
		if (this.fieldinfo == null) {
			this.fieldinfo = W3.tag(this.tagName);
			this.fieldinfo.replace(this);
			
			Struct funcwrap = StackUtil.queryVariable(state, StackUtil.stringFromSource(state, "FieldParams"));

			XElement pel = null;

			if (funcwrap instanceof XElement) {
				pel = (XElement) funcwrap;
			}
			else if (funcwrap instanceof AnyStruct) {
				pel = ((XElement) ((AnyStruct) funcwrap).getValue());
			}

			if (pel != null) {
				pel = pel.deepCopy();

				for (XNode node : pel.getChildren()) {
					this.fieldinfo.with(node);
				}
			}

			this.children = new CopyOnWriteArrayList<>();
		}
	}
	
	@Override
	public void renderBeforeChildren(InstructionWork state) throws OperatingContextException {
		this.initFieldInfo(state);	// should already be called by subclass
		
		if (this.hasNotEmptyAttribute("id")) 
			this.fieldid = this.getAttribute("id");
		else 
			this.fieldid = "gen" + RndUtil.nextUUId();
		
		boolean usespacer = ! this.getAttributeAsBooleanOrFalse("NoSpacer");
		
		if (this.hasNotEmptyAttribute("Label"))
			this.with(W3.tag("div")
				.withClass("dc-label")
				.with(W3.tag("label")
					.withAttribute("for", this.fieldid)
					.withText(this.getAttribute("Label") + ":")
				)
			)
			.withAttribute("data-dcf-label", this.getAttribute("Label"));
		else if (usespacer)
			this.with(W3.tag("div").withClass("dc-spacer"));
		
		String cmpt = Struct.objectToBooleanOrFalse(this.fieldinfo.getAttribute("ValidateButton")) ? "dc-compact" : null;
		
		if ((this.fieldinfo.find("Instructions") != null) || this.hasNotEmptyAttribute("Instructions")) {
			XElement inst = W3.tag("div")
					.withAttribute("class", "dc-message dc-message-info");
			
			XElement instsrc = this.fieldinfo.find("Instructions");
			
			if (instsrc != null) {
				XElement root = UIUtil.translate(state, instsrc, false);
				
				if (root != null) {
					// root is just a container and has no value
					inst.replaceChildren(root);
				}
				else {
					// TODO add warning if guest, add symbol if CMS
				}
			}
			else {
				inst.withText(this.getAttribute("Instructions"));
			}
			
			this.with(W3.tag("div")
				.withClass("dc-control")
				.withClass(cmpt)
				.with(inst)
			);
			
			// add spacer before input
			if (usespacer)
				this.with(W3.tag("div").withClass("dc-spacer").withClass(cmpt));
		}
		
		this.addControl(state);
		
		// add spacer before error message 
		if (usespacer)
			this.with(W3.tag("div").withClass("dc-spacer", "dc-valid-hidden").withClass(cmpt));
		
		Base inst = (Base) W3.tag("div")
				.withClass("dc-message", "dc-message-danger");
		
		if ((this.fieldinfo.find("Message") != null) || this.hasNotEmptyAttribute("Message")) {
			XElement instsrc = this.fieldinfo.find("Message");
			
			if (instsrc != null) {
				XElement root = UIUtil.translate(state, instsrc, false);
				
				if (root != null) {
					// root is just a container and has no value
					inst.replaceChildren(root);
				}
				else {
					// TODO add warning if guest, add symbol if CMS
				}
			}
			else {
				inst.withText(this.getAttribute("Message"));
			}
			
			inst.withClass("dc-fixed-message");
		}
		
		this.with(W3.tag("div")
			.withClass("dc-control", "dc-valid-hidden")
			.withClass(cmpt)
			.with(inst)
		);
	}
	
	@Override
	public void renderAfterChildren(InstructionWork state) throws OperatingContextException {
		this
			.withClass("dc-field")
			.withAttribute("id", "fld" + this.fieldid);
		
		Form frm = this.getForm(state);
		
		if (this.getAttributeAsBooleanOrFalse("Invalid") || ((frm != null) && frm.getAttributeAsBooleanOrFalse("Invalid")))
			this.withClass("dc-invalid");
		
		if (this.getAttributeAsBooleanOrFalse("Stacked") || ((frm != null) && frm.getAttributeAsBooleanOrFalse("Stacked")))
			this.withClass("dc-field-stacked");
		
		this.setName("div");
	}
}
