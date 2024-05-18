/* ************************************************************************
#
#  designCraft.io
#
#  http://designcraft.io/
#
#  Copyright:
#    Copyright 2014 eTimeline, LLC. All rights reserved.
#
#  License:
#    See the license.txt file in the project's top-level directory for details.
#
#  Authors:
#    * Andy White
#
************************************************************************ */
package dcraft.mail.dcc.inst;

import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
import dcraft.locale.LocaleDefinition;
import dcraft.locale.LocaleUtil;
import dcraft.script.StackUtil;
import dcraft.script.inst.Instruction;
import dcraft.script.inst.doc.Base;
import dcraft.script.work.*;
import dcraft.struct.BaseStruct;
import dcraft.struct.FieldStruct;
import dcraft.struct.RecordStruct;
import dcraft.struct.scalar.StringStruct;
import dcraft.task.IParentAwareWork;
import dcraft.util.StringUtil;
import dcraft.web.ui.inst.IncludeFragmentInline;
import dcraft.web.ui.inst.W3;
import dcraft.web.ui.inst.W3Closed;
import dcraft.xml.XElement;
import dcraft.xml.XNode;

import java.util.List;
import java.util.Map;

public class Html extends Base {
	static public Html tag() {
		Html el = new Html();
		el.setName("dcc.Html");
		return el;
	}

	/*
	static public void mergePageVariables(StackWork state, Base source) throws OperatingContextException {
		// RecordStruct page = (RecordStruct) StackUtil.queryVariable(state, "Page");
	}

	 */

	protected Map<String, String> hiddenAttributes = null;
	protected List<XNode> hiddenChildren = null;
	protected boolean headDone = false;

	@Override
	public XElement newNode() {
		return Html.tag();
	}
	
	@Override
	public void renderBeforeChildren(InstructionWork state) throws OperatingContextException {
		/// TODO Html.mergePageVariables(state, this);

		String templatePath = StackUtil.stringFromSource(state, "Template");

		if (StringUtil.isNotEmpty(templatePath)) {
			this.with(IncludeFragmentInline.tag()
					.withAttribute("Path", templatePath));
		}
	}
	
	@Override
	public void cleanup(InstructionWork state) throws OperatingContextException {
		if (this.headDone)
			return;
		
		super.cleanup(state);

		// TODO grab <Subject> - resolveValueToString - put into Config

		RecordStruct process = (RecordStruct) StackUtil.queryVariable(state, "_Process");
		XElement subjectX = this.selectFirst("Subject");

		if ((process != null) && (subjectX != null) && subjectX.hasText()) {
			String subject = subjectX.getText();

			if (StringUtil.isNotEmpty(subject)) {
				subject = StackUtil.resolveValueToString(state, subject);
				process.selectAsRecord("Config").with("Subject", subject);
			}
		}

		/*
		RecordStruct emailVar = (RecordStruct) StackUtil.queryVariable(state, "_Email");

		if (emailVar != null) {
			// TODO ??? no title?

			// fall back on Title attribute
			if (emailVar.isFieldEmpty("Title")) {
				String title = StackUtil.stringFromSource(state, "Title");

				if (StringUtil.isNotEmpty(title))
					emailVar.with("Title", title);
			}

			// make sure page variables are resolved (clean references)
			for (FieldStruct fld : emailVar.getFields()) {
				BaseStruct value = fld.getValue();

				if (value instanceof StringStruct) {
					StringStruct svalue = (StringStruct) value;

					svalue.setValue(StackUtil.resolveValueToString(state, svalue.getValueAsString(), true));
				}
			}
		}

		 */
		
		// after cleanup the document will be turned in just body by Base
		// we only want head and body in translated document
		// set apart the rest for possible use later in dynamic out
		this.hiddenAttributes = this.attributes;
		this.hiddenChildren = this.children;
	}
	
	@Override
	public void renderAfterChildren(InstructionWork state) throws OperatingContextException {
		// don't run twice
		if (this.headDone)
			return;

		Base body = (Base) this.find("body");
		
		if (body == null) {
			body = new Fragment();
			
			body
					.with(W3.tag("h1")
							.withText("Missing Body Error!!")
					);
			
			this.with(body);
		}

		W3 head = W3.tag("head");

		head
				.with(W3Closed.tag("meta")
						.withAttribute("chartset", "utf-8")
				)
				.with(W3Closed.tag("meta")
						.withAttribute("name", "viewport")
						.withAttribute("content", "width=device-width")
				)
				.with(W3Closed.tag("meta")
						.withAttribute("http-equiv", "X-UA-Compatible")
						.withAttribute("content", "IE=edge")
				)
				.with(W3Closed.tag("meta")
						.withAttribute("name", "x-apple-disable-message-reformatting")
				)
				.with(W3Closed.tag("meta")
						.withAttribute("name", "format-detection")
						.withAttribute("content", "telephone=no,address=no,email=no,date=no,url=no")
				)
				.with(W3Closed.tag("meta")
						.withAttribute("name", "color-scheme")
						.withAttribute("content", "light dark")
				)
				.with(W3Closed.tag("meta")
						.withAttribute("name", "supported-color-schemes")
						.withAttribute("content", "light dark")
				)
				.with(W3.tag("title").withText("{$_Process.Config.Subject}"));

		for (XNode rel : this.hiddenChildren) {
			if ((rel instanceof XElement) && ((XElement) rel).getName().equals("style"))
				head.with(rel);
		}

		// put head at top
		this.add(0, head);

		LocaleDefinition ldef = OperationContext.getOrThrow().getLocaleDefinition();

		this
				.withAttribute("xmlns", "http://www.w3.org/1999/xhtml")
				.withAttribute("xmlns:v", "urn:schemas-microsoft-com:vml")
				.withAttribute("xmlns:o", "urn:schemas-microsoft-com:office:office")
				.withAttribute("x-data-lang", ldef.getLanguage())
				.withAttribute("lang", LocaleUtil.htmlizeCode(ldef.getLanguage()))
				.withAttribute("dir", ldef.isRightToLeft() ? "rtl" : "ltr");

		super.renderAfterChildren(state);
		
		this.setName("html");
	}
	
	@Override
	public ReturnOption run(InstructionWork state) throws OperatingContextException {
		ReturnOption ret = super.run(state);
		
		if (ret == ReturnOption.DONE) {
			if (this.headDone) {
				return ret;
			}

			this.headDone = true;

			if (this.gotoHead(state))
				return ReturnOption.CONTINUE;
		}
		
		return ret;
	}

	@Override
	public boolean gotoNext(InstructionWork state, boolean orTop) {
		// don't go further if the head is running as that was the last step
		if (this.headDone)
			return false;

		return super.gotoNext(state, orTop);
	}

	public boolean gotoHead(InstructionWork state) {
		BlockWork blockWork = (BlockWork) state;

		XElement head = this.find("head");

		if (! (head instanceof Instruction))
			return false;

		blockWork.setCurrEntry(((Instruction) head).createStack(state));
		return true;
	}

	@Override
	public ReturnOption operation(StackWork stack, XElement code) throws OperatingContextException {
		/*
		if ("Require".equals(code.getName())) {
			XElement copy = code.deepCopy();

			UIUtil.cleanDocReferences(stack, copy);

			this.add(copy);

			return ReturnOption.CONTINUE;
		}
		 */

		return super.operation(stack, code);
	}

	@Override
	public InstructionWork createStack(IParentAwareWork state) {
		return MainWork.of(state, this);
	}
	
	public InstructionWork createStack() {
		return MainWork.of(null, this);
	}
}
