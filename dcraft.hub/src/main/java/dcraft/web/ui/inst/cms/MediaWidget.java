package dcraft.web.ui.inst.cms;

import dcraft.hub.op.OperatingContextException;
import dcraft.script.work.InstructionWork;
import dcraft.util.StringUtil;
import dcraft.web.ui.inst.W3;
import dcraft.xml.XElement;

import java.util.List;

public class MediaWidget {
	/* TODO
	extends
} Section {
	static public MediaSection tag() {
		MediaSection el = new MediaSection();
		el.setName("dc.MediaSection");
		return el;
	}
	
	@Override
	public XElement newNode() {
		return MediaSection.tag();
	}
	
	@Override
	public void renderBeforeChildren(InstructionWork state) throws OperatingContextException {
		Long maximgs = this.hasNotEmptyAttribute("Max") ? StringUtil.parseInt(this.getAttribute("Max")) : null;
		
	    String template = "";
		XElement xtemp = this.find("Template");
		XElement txtemp = this.find("TextTemplate");
		
		if (xtemp != null) 
			template = xtemp.toInnerString();
		else if (txtemp != null) 
			template = txtemp.getText();
		else 
			template = this.getText();
		
	    if (StringUtil.isEmpty(template))
	    	template = "<a href=\"#\" data-dc-kind=\"@mediakind@\"><img src=\"@path@\" data-dc-media=\"@mediadata@\" /></a>";
	    
	    List<XElement> medias = this.selectAll("Media");
	    
	    this.clearChildren();
	    
	    /* TODO cleanup
	    // now build up the xml for the content
	    StringBuilder out = new StringBuilder();
	
	    out.append("<div>");
	
	    int currimg = 0;
	    
	    for (XElement media : medias) {
	    	long cidx = currimg++;

	    	if ((maximgs != null) && (cidx > maximgs))
	    		return;

	    	boolean checkmatches = true;

	    	String value = template;

	    	while (checkmatches) {
	    		checkmatches = false;
	    		Matcher m = UIUtil.macropatten.matcher(value);

	    		while (m.find()) {
	    			String grp = m.group();
	    			String macro = grp.substring(1, grp.length() - 1);
	    			String val = GalleryThumbs.expandMacro(media, macro);

	    			// if any of these, then replace and check (expand) again 
	    			if (val != null) {
	    				value = value.replace(grp, UIElement.quote(val));
	    				checkmatches = true;
	    			}
	    		}
	    	}

	    	out.append(value);
	    }
	            
	    out.append("</div>");
	
		UIElement lbox = (UIElement) OperationContext.getOrThrow().getSite().getWebsite().parseUI(out);
		
		if (lbox != null) {
			lbox
				.withAttribute("class", "dc-section-gallery-list");
				
			if (this.hasNotEmptyAttribute("ListId"))
				lbox.withAttribute("id", this.getAttribute("ListId"));
			
			this.add(lbox);
		}
	    else {
	    	Logger.warn("Error adding gallery section: ");
			OperationContext.getAsTaskOrThrow().clearExitCode();
	    }
	    *
	}
	
	@Override
	public void renderAfterChildren(InstructionWork state) throws OperatingContextException {
		super.renderAfterChildren(state);
		
		this.withClass("dc-section-media")
			.withAttribute("data-dc-enhance", "true")
			.withAttribute("data-dc-tag", this.getName())
			.withAttribute("data-dccms-plugin", "Media");
		
		if (this.hasNotEmptyAttribute("Title"))
			this.add(0, W3.tag("h2").withText(this.getAttribute("Title")));
	}
	*/
}
