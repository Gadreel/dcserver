package dcraft.mail.dcc.inst;

import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
import dcraft.locale.LocaleDefinition;
import dcraft.locale.LocaleUtil;
import dcraft.script.StackUtil;
import dcraft.script.inst.doc.Base;
import dcraft.script.work.InstructionWork;
import dcraft.web.ui.inst.W3;
import dcraft.xml.*;

import java.util.ArrayList;
import java.util.List;

public class Body extends Base {
	static public Body tag() {
		Body el = new Body();
		el.setName("dcc.Body");
		return el;
	}
	
	@Override
	public XElement newNode() {
		return Body.tag();
	}
	
	@Override
	public void renderBeforeChildren(InstructionWork state) throws OperatingContextException {
		// the children will move into the body, so clear out our child list
		List<XNode> hiddenchildren = this.children;

		this.children = new ArrayList<>();

		String background = XNode.quote(StackUtil.stringFromSource(state, "Background", "#ffffff").toLowerCase());

		String style = StackUtil.stringFromSource(state, "style", "");

		if (! style.contains("margin"))
			style += " margin: 0; ";

		if (! style.contains("padding"))
			style += " padding: 0 !important; ";

		if (! style.contains("background"))
			style += " background-color: " + background + "; ";

		style += " mso-line-height-rule: exactly; ";

		this
				.withClass("email-bg")
				.attr("width", "100%")
				.attr("style", style);

		LocaleDefinition ldef = OperationContext.getOrThrow().getLocaleDefinition();

		XElement bodyCenter = W3.tag("center")
				.withClass("email-bg")
				.attr("role", "article")
				.attr("aria-roledescription", "email")
				.attr("style", "width: 100%; background-color: " + background + ";")
				.attr("lang", LocaleUtil.htmlizeCode(ldef.getLanguage()))
				.attr("dir", ldef.isRightToLeft() ? "rtl" : "ltr");

		this.add(bodyCenter);

		StringBuilder sbTableStart = new StringBuilder()
				.append("[if mso | IE]>\r\n")
				.append("<table role=\"presentation\" border=\"0\" cellpadding=\"0\" cellspacing=\"0\" width=\"100%\" style=\"background-color: " + background + ";\" class=\"email-bg\">\r\n")
				.append("<tr>")
				.append("<td>")
				.append("<![endif]");

		XComment bodyTableStart = XComment.of(sbTableStart.toString());

		bodyCenter.with(bodyTableStart);

		// find and expand preview text

		if (hiddenchildren != null) {
			for (XNode n : hiddenchildren) {
				if ((n instanceof XElement) && "PreviewText".equals(((XElement) n).getName())) {
					StringBuilder previewText = new StringBuilder();

					((XElement) n).gatherText(previewText);

					XElement previewSect = W3.tag("div")
							.attr("style", "max-height:0; overflow:hidden; mso-hide:all;")
							.attr("aria-hidden", "true")
							.with(XText.of(previewText.toString()));

					bodyCenter.with(previewSect);

					// preview buffer spacing
					XElement spacingSect = W3.tag("div")
							.attr("style", "display: none; font-size: 1px; line-height: 1px; max-height: 0px; max-width: 0px; opacity: 0; overflow: hidden; mso-hide: all; font-family: sans-serif;")
							.with(XText.ofRaw("&zwnj;&nbsp;&zwnj;&nbsp;&zwnj;&nbsp;&zwnj;&nbsp;&zwnj;&nbsp;&zwnj;&nbsp;&zwnj;&nbsp;&zwnj;&nbsp;&zwnj;&nbsp;&zwnj;&nbsp;&zwnj;&nbsp;&zwnj;&nbsp;&zwnj;&nbsp;&zwnj;&nbsp;&zwnj;&nbsp;&zwnj;&nbsp;&zwnj;&nbsp;&zwnj;&nbsp;"));

					bodyCenter.with(spacingSect);
				}
			}
		}

		// add non-preview children

		if (hiddenchildren != null) {
			for (XNode n : hiddenchildren) {
				bodyCenter.add(n);
			}
		}

		StringBuilder sbTableEnd = new StringBuilder()
				.append("[if mso | IE]>\r\n")
				.append("</td>")
				.append("</tr>")
				.append("</table>")
				.append("<![endif]");

		XComment bodyTableEnd = XComment.of(sbTableEnd.toString());

		bodyCenter.with(bodyTableEnd);
	}
	
	@Override
	public void renderAfterChildren(InstructionWork state) {
		this.setName("body");
	}
}
