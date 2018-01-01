package dcraft.web.ui.inst.feed;

import java.util.ArrayList;

import dcraft.hub.op.OperatingContextException;
import dcraft.script.work.InstructionWork;
import dcraft.struct.Struct;
import dcraft.script.inst.doc.Base;
import dcraft.xml.XElement;

public class FeedDetail extends Base {
	static public FeedDetail tag() {
		FeedDetail el = new FeedDetail();
		el.setName("dcm.FeedDetail");
		return el;
	}
	
	@Override
	public XElement newNode() {
		return FeedDetail.tag();
	}
	
	@Override
	public void renderBeforeChildren(InstructionWork state) throws OperatingContextException {
		String channel = this.getAttribute("Channel");
		boolean pagemain = this.getAttributeAsBooleanOrFalse("PageMain");
		int pathpart = Struct.objectToInteger(this.getAttribute("PathPart", "1")).intValue();
		//WebController wctrl = (WebController) OperationContext.getOrThrow().getController();

		/*
		<dcm.FeedDetail Channel="Announcements" PathPart="1" PageMain="true">
			<Template>
				<div class="event-title"><h3>@val|Field|Title@</h3></div>
				<dc.Markdown class="event-content"><![CDATA[@val|Field|Summary@]]></dc.Markdown>
			</Template>
		</dcm.FeedDetail>
		 * 
		 */
		
		this.withAttribute("data-dcm-channel", channel);
		
		XElement tel = this.find("Template");
		
		XElement mtel = this.find("MissingTemplate");

		// start with clean children
		this.children = new ArrayList<>();
		
		if ((tel == null) || (mtel == null))
			return;
   
		/* TODO restore
        // now build up the xml for the content
        StringBuilder out = new StringBuilder();

        out.append("<div>");

		CommonPath fpath = wctrl.getPath().subpath(pathpart);
		int pdepth = fpath.getNameCount();
		
		// check file system
		while (pdepth > 0) {
			CommonPath ppath = fpath.subpath(0, pdepth);
			
			// possible to override the file path and grab a random Page from `feed`
			String cmspath = ppath.toString();
			
			FeedAdapter feed = FeedAdapter.from(channel, cmspath, wctrl.getView());
			
			if (feed != null) {
				FeedParams ftemp = new FeedParams();
				ftemp.setFeedData(feed);
				
				this.withAttribute("data-dcm-path", cmspath);
				
				String template = tel.getText();
				  
				String value = ftemp.expandMacro(template);
				 
				value = value.replace("*![CDATA[", "<![CDATA[").replace("]]*", "]]>");
				
				out.append(value);
				
				if (pagemain)
					UIUtil.decorateHtmlPageUI(feed, this.getRoot());
				
				break;
			}
			
			pdepth--;
		}

		if (pdepth == 0) {
			String template = mtel.getText();
			
			if (StringUtil.isNotEmpty(template)) {
				String value = this.expandMacro(template);
				 
				if (StringUtil.isNotEmpty(template)) {
					value = value.replace("*![CDATA[", "<![CDATA[").replace("]]*", "]]>");
					
					out.append(value);
				}
			}
		}
		
        out.append("</div>");

		XElement lbox = OperationContext.getAsTaskOrThrow().getSite().getWebsite().parseUI(out);
		
		if (lbox != null) {
			this.replaceChildren(lbox);
		}
		else {
			// TODO
			//pel.add(new UIElement("div")
			//	.withText("Error parsing section."));

        	Logger.warn("Error adding feed detail: ");
        	OperationContext.getAsTaskOrThrow().clearExitCode();
        }
		
		this.with(new Button("dcmi.EditFeedButton")
				.withClass("dcuiSectionButton", "dcuiCmsi")
				.withAttribute("Icon", "fa-cog")
			);
        */
	}
	
	@Override
	public void renderAfterChildren(InstructionWork state) throws OperatingContextException {
		this
			.withClass("dcm-cms-editable", "dcm-feed-detail")
			.withAttribute("data-dccms-edit", this.getAttribute("AuthTags", "Editor,Admin,Developer"))	// TODO get from channel def, for Feed too
			.withAttribute("data-dc-enhance", "true")
			.withAttribute("data-dc-tag", this.getName());
		
		this.setName("div");
	}
}
