package dcraft.web.ui.inst.cms;

import dcraft.hub.op.OperatingContextException;
import dcraft.script.StackUtil;
import dcraft.script.work.InstructionWork;
import dcraft.script.inst.doc.Base;
import dcraft.web.ui.inst.W3;
import dcraft.xml.XElement;

public class YouTubeWidget extends Base {
	static public YouTubeWidget tag() {
		YouTubeWidget el = new YouTubeWidget();
		el.setName("dcm.ImageWidget");
		return el;
	}
	
	@Override
	public XElement newNode() {
		return YouTubeWidget.tag();
	}
	
	@Override
	public void renderBeforeChildren(InstructionWork state) throws OperatingContextException {
		String vid = StackUtil.stringFromSource(state,"VideoId");
		// 19by9 or 4by3
		String ratio = StackUtil.stringFromSource(state,"Ratio", "16by9");
		
		this.with(W3.tag("div")
				.withClass("dc-media-box", "dc-media-video", "dc-youtube-container-" + ratio)
				.with(W3.tag("img").withAttribute("src", "/imgs/" + ratio + ".png"))
				.with(W3.tag("iframe")
						.withAttribute("src", "https://www.youtube.com/embed/" + vid + "?html5=1&rel=0&showinfo=0")
						.withAttribute("frameborder", "0")
						.withAttribute("allowfullscreen", "allowfullscreen")
				)
		);
	}
	
	@Override
	public void renderAfterChildren(InstructionWork state) {
		this
				.withClass("dc-widget dc-widget-video")
				.withAttribute("data-dc-enhance", "true")
				.withAttribute("data-dc-tag", this.getName());
		
		this.setName("div");
    }
}
