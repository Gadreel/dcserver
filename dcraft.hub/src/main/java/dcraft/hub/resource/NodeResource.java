package dcraft.hub.resource;

import dcraft.filestore.CommonPath;
import dcraft.hub.ResourceHub;
import dcraft.log.Logger;
import dcraft.script.IOperator;
import dcraft.script.Script;
import dcraft.struct.format.IFormatter;
import dcraft.util.FileUtil;
import dcraft.util.StringUtil;
import dcraft.xml.XElement;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class NodeResource extends ResourceBase {
	protected List<Path> modules = new ArrayList<>();
	protected List<Path> scripts = new ArrayList<>();

	public NodeResource() {
		this.setName("Node");
	}
	
	public NodeResource getParentResource() {
		if (this.tier == null)
			return null;
		
		ResourceTier pt = this.tier.getParent();
		
		if (pt != null)
			return pt.getNodes();
		
		return null;
	}

	public NodeResource withModule(Path... v) {
		for (Path p : v) {
			Path n = p.normalize();

			if (n.startsWith("/"))
				n = FileUtil.getAppPath().relativize(n);

			this.modules.add(n);
		}

		return this;
	}

	public NodeResource withScript(Path... v) {
		for (Path p : v) {
			Path n = p.normalize();

			if (n.startsWith("/"))
				n = FileUtil.getAppPath().relativize(n);

			this.scripts.add(n);
		}


		return this;
	}

	public Path findScript(CommonPath path) {
		for (int i = this.scripts.size() - 1; i >= 0; i--) {
			Path path1 = this.scripts.get(i).resolve(path.toString().substring(1));
			
			if (Files.exists(path1))
				return path1;
		}
		
		NodeResource parent = this.getParentResource();
		
		if (parent != null)
			return parent.findScript(path);
		
		return null;
	}

	public String buildClassPath() {
		StringBuilder sb = new StringBuilder();

		this.buildClassPath(sb);

		return sb.toString();
	}

	public void buildClassPath(StringBuilder sb) {
		this.addLocalClassPath(sb);

		NodeResource parent = this.getParentResource();

		if (parent != null)
			parent.buildClassPath(sb);
	}

	public void addLocalClassPath(StringBuilder sb) {
		for (int i = this.modules.size() - 1; i >= 0; i--) {
			if (sb.length() > 0)
				sb.append(':');

			sb.append("./");
			sb.append(modules.get(i).toString());
		}
	}
}
