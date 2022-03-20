package dcraft.web.ui.inst.form;

import dcraft.hub.op.OperatingContextException;
import dcraft.script.work.InstructionWork;
import dcraft.web.ui.UIUtil;
import dcraft.xml.XElement;

public class Search extends InputField {
	static public Search tag() {
		Search el = new Search();
		el.setName("dcf.Search");
		return el;
	}
	
	@Override
	public XElement newNode() {
		return Search.tag();
	}

	@Override
	public void renderBeforeChildren(InstructionWork state) throws OperatingContextException {
		this.with(
			XElement.tag("Input"),
			XElement.tag("Button")
				.attr("Icon", "fas/search")
				.attr("class", "open"),
			XElement.tag("Button")
				.attr("Icon", "fas/times")
				.attr("class", "close")
		);

		super.renderBeforeChildren(state);
	}

	@Override
	public void renderAfterChildren(InstructionWork state) throws OperatingContextException {
		UIUtil.requireStyle(this, state, "/css/includes/dcf.search.css");
		UIUtil.requireScript(this, state, "/js/includes/dcf.search.js");

		super.renderAfterChildren(state);
	}
}
