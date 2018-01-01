package dcraft.web.md.plugin;

import java.util.List;
import java.util.Map;

import dcraft.web.md.Plugin;
import dcraft.web.md.ProcessContext;
import dcraft.xml.XElement;

public class HtmlSection extends Plugin {
	public HtmlSection() {
		super("HtmlSection");
	}

	@Override
	public void emit(ProcessContext ctx, XElement parent, List<String> lines, Map<String, String> params) {
		/* TODO review
		Base pel = dcraft.web.ui.inst.cms.HtmlSection.tag();
		
		if (params.containsKey("Id"))
			pel.withAttribute("id", params.get("Id"));
		
		if (params.containsKey("Lang"))
			pel.withAttribute("lang", params.get("Lang"));
		
		if (params.containsKey("Class"))
			pel.withClass(params.get("Class").split(" "));
		
		if (params.containsKey("Hidden"))
			pel.withAttribute("Hidden", params.get("Hidden"));
	
        StringBuilder in = new StringBuilder();
        
        in.append("<div>");

        for (String n : lines)
        	in.append(n).append("\n");
        
        in.append("</div>");
        
        try {
			XElement cbox = ScriptHub.parseInstructions(in);

        	if (cbox != null) {
    			// copy all children
    			for (XNode n : cbox.getChildren())
    				pel.add(n);
        	}
        }
        catch (Exception x) {
			pel.with(XElement.tag("InvalidContent"));
        	Logger.warn("Error adding html box " + x);
        }
		
		parent.with(pel);
		*/
	}
}
