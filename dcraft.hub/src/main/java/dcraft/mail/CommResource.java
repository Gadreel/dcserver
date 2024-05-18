package dcraft.mail;

import dcraft.filestore.CommonPath;
import dcraft.hub.ResourceHub;
import dcraft.hub.resource.ResourceBase;
import dcraft.hub.resource.ResourceTier;
import dcraft.log.Logger;
import dcraft.script.IOperator;
import dcraft.script.Script;
import dcraft.struct.format.IFormatter;
import dcraft.util.StringUtil;
import dcraft.xml.XElement;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class CommResource extends ResourceBase {
	protected Path path = null;

	protected Map<String, XElement> emailhandlers = new HashMap<>();

	public CommResource() {
		this.setName("Comm");
	}
	
	public CommResource getParentResource() {
		if (this.tier == null)
			return null;
		
		ResourceTier pt = this.tier.getParent();
		
		if (pt != null)
			return pt.getComm();
		
		return null;
	}
	
	public CommResource withPath(Path v) {
		this.path = v;
		return this;
	}

	public void addEmailHandler(XElement handler) {
		String name = handler.getAttribute("Name");

		if (StringUtil.isNotEmpty(name))
			this.emailhandlers.put(name, handler);
	}

	public XElement getEmailHandler(String name) {
		if (StringUtil.isNotEmpty(name))
			return this.emailhandlers.get(name);

		return null;
	}

	public Set<String> getEmailHandlers() {
		return this.emailhandlers.keySet();
	}

	public Script loadScript(CommonPath path) {
		Path path1 = this.path.resolve(path.toString().substring(1));

		if (Files.exists(path1))
			return Script.of(path1);

		CommResource parent = this.getParentResource();
		
		if (parent != null)
			return parent.loadScript(path);
		
		return null;
	}

	public Path findCommFile(CommonPath path) {
		Path path1 = this.path.resolve(path.toString().substring(1));

		if (Files.exists(path1))
			return path1;

		CommResource parent = this.getParentResource();

		if (parent != null)
			return parent.findCommFile(path);

		return null;
	}
}
