package dcraft.web.ui.inst.feed;

import dcraft.hub.app.ApplicationHub;
import dcraft.hub.op.OperatingContextException;
import dcraft.script.work.InstructionWork;
import dcraft.util.StringUtil;
import dcraft.script.inst.doc.Base;
import dcraft.web.ui.inst.W3;
import dcraft.xml.XElement;

public class TwitterTimelineLoader extends Base {
	static public TwitterTimelineLoader tag() {
		TwitterTimelineLoader el = new TwitterTimelineLoader();
		el.setName("dcm.TwitterTimelineLoader");
		return el;
	}
	
	@Override
	public XElement newNode() {
		return TwitterTimelineLoader.tag();
	}
	
	@Override
	public void renderAfterChildren(InstructionWork state) throws OperatingContextException {
		this
			.withAttribute("data-dc-enhance", "true")
			.withAttribute("data-dc-tag", this.getName());
		
		this.setName("div");
		
		this.withClass("dcm-tw-listing");
		
		String alternate = this.getAttribute("Alternate");
		
		String name = "Twitter";
		
		XElement twsettings = ApplicationHub.getCatalogSettings(name, alternate);
		
		if (twsettings != null) {
			String scrname = twsettings.getAttribute("ScreenName");
			
			if (StringUtil.isNotEmpty(scrname)) {
				this
					.with(W3.tag("a")
						.withClass("twitter-timeline")
						.withAttribute("href", "https://twitter.com/" + scrname)
						.withAttribute("target", "_blank")
						.withText("Tweets by @" + scrname)
					);
			}
		}
    }
}
