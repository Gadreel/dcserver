package dcraft.web.md.plugin;

import java.util.List;
import java.util.Map;

import dcraft.web.md.Plugin;
import dcraft.web.md.ProcessContext;
import dcraft.xml.XElement;

public class PairedMediaSection extends Plugin {
	public PairedMediaSection() {
		super("PairedMediaSection");
	}

	@Override
	public void emit(ProcessContext ctx, XElement parent, List<String> lines, Map<String, String> params) {
		/* TODO review
		Base pel = dcraft.web.ui.inst.cms.PairedMediaSection.tag();
		
		if (params.containsKey("Id"))
			pel.withAttribute("id", params.get("Id"));
		
		if (params.containsKey("Lang"))
			pel.withAttribute("lang", params.get("Lang"));
		
		if (params.containsKey("Class"))
			pel.withClass(params.get("Class").split(" "));
		
		if (params.containsKey("Hidden"))
			pel.withAttribute("Hidden", params.get("Hidden"));
		
		if (params.containsKey("Image"))
			pel.withAttribute("Image", params.get("Image"));
		
		if (params.containsKey("YouTubeId"))
			pel.withAttribute("YouTubeId", params.get("YouTubeId"));
		
		if (params.containsKey("YouTubeUrl"))
			pel.withAttribute("YouTubeUrl", params.get("YouTubeUrl"));
		
		if (params.containsKey("MediaId"))
			pel.withAttribute("MediaId", params.get("MediaId"));
		
		if (params.containsKey("MediaTitle"))
			pel.withAttribute("MediaTitle", params.get("MediaTitle"));
		
        StringBuilder in = new StringBuilder();

        for (String n : lines)
        	in.append(n).append("\n");
         
        String template = in.toString(); 		
        String copy = null;
        
        int splitpos = template.indexOf("\n---\n");

        if ((splitpos == -1) && template.startsWith("---\n")) {
        	copy = template.substring(5);
        	template = "";
        }
        else if (splitpos == -1) {
        	copy = template;
        	template = "";
        }
        else if (splitpos != -1) {
        	copy = template.substring(splitpos + 6);
        	template = template.substring(0, splitpos);
        }
        
        pel.with(XElement.tag("TextTemplate").withText(template));
        pel.with(XElement.tag("Content").withText(copy));
        
		parent.with(pel);
		*/
	}
}
