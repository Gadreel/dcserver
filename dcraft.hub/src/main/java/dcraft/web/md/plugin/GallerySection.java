package dcraft.web.md.plugin;

import java.util.List;
import java.util.Map;

import dcraft.web.md.Plugin;
import dcraft.web.md.ProcessContext;
import dcraft.xml.XElement;

public class GallerySection extends Plugin {
	public GallerySection() {
		super("GallerySection");
	}

	@Override
	public void emit(ProcessContext ctx, XElement parent, List<String> lines, Map<String, String> params) {
		/* TODO review
		Base pel = dcraft.web.ui.inst.cms.GallerySection.tag();
			
		if (params.containsKey("Id"))
			pel.withAttribute("id", params.get("Id"));
		
		if (params.containsKey("Lang"))
			pel.withAttribute("lang", params.get("Lang"));
		
		if (params.containsKey("Class"))
			pel.withClass(params.get("Class").split(" "));
		
		if (params.containsKey("Path"))
			pel.withAttribute("Path", params.get("Path"));
		
		if (params.containsKey("Show"))
			pel.withAttribute("Show", params.get("Show"));
		
		if (params.containsKey("Hidden"))
			pel.withAttribute("Hidden", params.get("Hidden"));
		
		if (params.containsKey("Variant"))
			pel.withAttribute("Variant", params.get("Variant"));
		
		if (params.containsKey("Extension"))
			pel.withAttribute("Extension", params.get("Extension"));
		
		if (params.containsKey("Title"))
			pel.withAttribute("Title", params.get("Title"));

		if (params.containsKey("ListId"))
			pel.withAttribute("ListId", params.get("ListId"));

		if (params.containsKey("Max"))
			pel.withAttribute("Max", params.get("Max"));
		
        StringBuilder in = new StringBuilder(); 
        
        for (String n : lines) 
          in.append(n).append("\n"); 
         
        String template = in.toString(); 		
        
        pel.with(XElement.tag("TextTemplate").withText(template));
        
		parent.with(pel);
		*/
	}
}
