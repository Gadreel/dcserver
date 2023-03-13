package dcraft.web.ui.inst.cms;

import dcraft.filestore.CommonPath;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
import dcraft.locale.LocaleUtil;
import dcraft.script.StackUtil;
import dcraft.script.work.InstructionWork;
import dcraft.script.inst.doc.Base;
import dcraft.struct.FieldStruct;
import dcraft.struct.RecordStruct;
import dcraft.struct.Struct;
import dcraft.struct.format.YouTubeId;
import dcraft.util.StringUtil;
import dcraft.web.ui.UIUtil;
import dcraft.web.ui.inst.ICMSAware;
import dcraft.web.ui.inst.W3;
import dcraft.xml.XElement;
import dcraft.xml.XNode;

public class YouTubeWidget extends Base implements ICMSAware {
	static public YouTubeWidget tag() {
		YouTubeWidget el = new YouTubeWidget();
		el.setName("dcm.YouTubeWidget");
		return el;
	}
	
	@Override
	public XElement newNode() {
		return YouTubeWidget.tag();
	}
	
	@Override
	public void renderBeforeChildren(InstructionWork state) throws OperatingContextException {
		String vid = YouTubeId.getIdFromUrl(StackUtil.stringFromSource(state,"VideoId"));
		// 19by9 or 4by3
		String ratio = StackUtil.stringFromSource(state,"Ratio", "16by9");
		String mod = "";

		if (StackUtil.boolFromSource(state, "Autoplay"))
			mod += "&autoplay=1";

		this.with(W3.tag("div")
				.withClass("dc-media-box", "dc-media-video", "dc-youtube-container-" + ratio)
				.with(W3.tag("img")
						.attr("src", "/imgs/" + ratio + ".png")
						.attr("alt", "")
				)
				.with(W3.tag("iframe")
						.withAttribute("src", "https://www.youtube.com/embed/" + vid + "?html5=1&rel=0&showinfo=0" + mod)
						.withAttribute("frameborder", "0")
						.withAttribute("allowfullscreen", "allowfullscreen")
						.withAttribute("allow", "autoplay;")
				)
		);
		
		UIUtil.markIfEditable(state, this, "widget");
	}
	
	@Override
	public void renderAfterChildren(InstructionWork state) {
		this
				.withClass("dc-widget dc-widget-video")
				.withAttribute("data-dc-enhance", "true")
				.withAttribute("data-dc-tag", this.getName());
		
		this.setName("div");
    }
	
	@Override
	public boolean applyCommand(CommonPath path, XElement root, RecordStruct command) throws OperatingContextException {
		String cmd = command.getFieldAsString("Command");
		
		if ("UpdatePart".equals(cmd)) {
			// TODO check that the changes made are allowed
			RecordStruct params = command.getFieldAsRecord("Params");
			String area = params.selectAsString("Area");
			
			if ("Props".equals(area)) {
				RecordStruct props = params.getFieldAsRecord("Properties");
				
				if (props != null) {
					for (FieldStruct fld : props.getFields()) {
						if (fld.getValue() != null)
							this.attr(fld.getName(), Struct.objectToString(fld.getValue()));
						else
							this.removeAttribute(fld.getName());
					}
				}

				return true;
			}
		}
		
		return false;
	}
}
