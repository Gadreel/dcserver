package dcraft.web.md;

import dcraft.xml.XElement;

import java.util.List;
import java.util.Map;

public abstract class Plugin {
	protected String idPlugin;
	
	public Plugin(String idPlugin) {
		this.idPlugin = idPlugin;
	}

	public abstract void emit(ProcessContext ctx, XElement parent, List<String> lines, Map<String, String> params);

	public String getIdPlugin() {
		return idPlugin;
	}
}
