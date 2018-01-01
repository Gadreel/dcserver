package dcraft.web.md.plugin;

import java.util.List;
import java.util.Map;

import dcraft.web.md.Plugin;
import dcraft.web.md.ProcessContext;
import dcraft.xml.XElement;

public class StandardSection extends Plugin {
	public StandardSection() {
		super("StandardSection");
	}

	@Override
	public void emit(ProcessContext ctx, XElement parent, List<String> lines, Map<String, String> params) {
		/* TODO review
		Base pel = dcraft.web.ui.inst.cms.StandardSection.tag();
		
		if (params.containsKey("Id"))
			pel.withAttribute("id", params.get("Id"));
		
		if (params.containsKey("Lang"))
			pel.withAttribute("lang", params.get("Lang"));
		
		if (params.containsKey("Class"))
			pel.withClass(params.get("Class").split(" "));
		
		if (params.containsKey("Hidden"))
			pel.withAttribute("Hidden", params.get("Hidden"));
	
        StringBuilder in = new StringBuilder();

        for (String n : lines)
        	in.append(n).append("\n");
        
        pel.withText(in.toString());
		
		parent.with(pel);
		*/
	}
}
