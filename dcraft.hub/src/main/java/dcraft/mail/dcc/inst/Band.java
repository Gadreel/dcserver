package dcraft.mail.dcc.inst;

import dcraft.filestore.CommonPath;
import dcraft.hub.op.OperatingContextException;
import dcraft.script.StackUtil;
import dcraft.script.inst.doc.Base;
import dcraft.script.work.InstructionWork;
import dcraft.struct.FieldStruct;
import dcraft.struct.RecordStruct;
import dcraft.struct.Struct;
import dcraft.util.StringUtil;
import dcraft.web.ui.inst.W3;
import dcraft.xml.XComment;
import dcraft.xml.XElement;
import dcraft.xml.XNode;

import java.util.ArrayList;
import java.util.List;

public class Band extends Base {
	static public Band tag() {
		Band el = new Band();
		el.setName("dcc.Band");
		return el;
	}
	
	@Override
	public XElement newNode() {
		return Band.tag();
	}
	
	@Override
	public void renderBeforeChildren(InstructionWork state) throws OperatingContextException {
		// the children will move into the body, so clear out our child list
		List<XNode> hiddenchildren = this.children;
		
		this.children = new ArrayList<>();

		this
				.attr("role", "presentation")
				.attr("cellspacing", "0")
				.attr("cellpadding", "0")
				.attr("border", "0")
				.attr("width", "100%");

		// if there is a bleed background color, expect a already present class from developer, e.g. class="darkmode-fullbleed-bg"

		String style = StackUtil.stringFromSource(state, "style", "");

		String background = XNode.quote(StackUtil.stringFromSource(state, "Background", "").toLowerCase());

		if (StringUtil.isNotEmpty(background) && ! style.contains("background"))
			style += " background-color: " + background + "; ";

		this.withClass("dcc-band");

		// in pixels
		String width = StackUtil.stringFromSource(state,"Width", "680").toLowerCase();

		XElement emailContainer = W3.tag("div")
				.withClass("email-container", "dcc-band-wrapper")
				.attr("align", "center")
				.attr("style", "max-width: " + width + "px; margin: 0 auto; " + style);

		StringBuilder sbTableStart = new StringBuilder()
				.append("[if mso | IE]>\r\n")
				.append("<table role=\"presentation\" border=\"0\" cellpadding=\"0\" cellspacing=\"0\" width=\"" + width + "\" align=\"center\">\r\n")
				.append("<tr>\r\n")
				.append("<td>\r\n")
				.append("<![endif]");

		EmailComment bandTableStart = EmailComment.of(sbTableStart.toString());

		emailContainer.with(bandTableStart);

		XElement emailContainerCell = W3.tag("td");

		XElement emailContainerInner = W3.tag("table")
				.attr("role", "presentation")
				.attr("cellspacing", "0")
				.attr("cellpadding", "0")
				.attr("border", "0")
				.attr("width", "100%")
				.attr("style", "margin: auto;")
				.with(W3.tag("tr").with(emailContainerCell));

		if (hiddenchildren != null) {
			for (XNode n : hiddenchildren)
				emailContainerCell.add(n);
		}

		emailContainer.with(emailContainerInner);

		StringBuilder sbTableEnd = new StringBuilder()
				.append("[if mso | IE]>\r\n")
				.append("</td>\r\n")
				.append("</tr>\r\n")
				.append("</table>\r\n")
				.append("<![endif]");

		//XComment bandTableEnd = XComment.of(sbTableEnd.toString());
		EmailComment bandTableEnd = EmailComment.of(sbTableEnd.toString());

		emailContainer.with(bandTableEnd);

		this.with(
				W3.tag("tr").with(
						W3.tag("td").with(emailContainer)
				)
		);
	}

	@Override
	public void renderAfterChildren(InstructionWork state) throws OperatingContextException {
		this.setName("table");
	}
}
