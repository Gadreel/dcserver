package dcraft.script;

import dcraft.filestore.CommonPath;
import dcraft.hub.ResourceHub;
import dcraft.hub.resource.ResourceBase;
import dcraft.hub.resource.ResourceTier;
import dcraft.log.Logger;
import dcraft.scriptold.IOperator;
import dcraft.scriptold.mutator.Substring;
import dcraft.util.StringUtil;
import dcraft.xml.XElement;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ScriptResource extends ResourceBase {
	// operator is type specific - [Type][Name] = mutator
	protected Map<String, Map<String, IOperator>> operationExtensions = new HashMap<String, Map<String,IOperator>>();
	protected List<Path> paths = new ArrayList<>();
	
	// TODO someday restore - protected IDebuggerHandler debugger = null;

	public ScriptResource() {
		this.setName("Script");
		
		// TODO move and load from packages/settings
		HashMap<String, IOperator> stringextensions = new HashMap<String, IOperator>();
		stringextensions.put("Substring", new Substring());
		
		this.operationExtensions.put("String", stringextensions);
	}
	
	public ScriptResource getParentResource() {
		if (this.tier == null)
			return null;
		
		ResourceTier pt = this.tier.getParent();
		
		if (pt != null)
			return pt.getScripts();
		
		return null;
	}
	
	public ScriptResource withPath(Path... v) {
		for (Path p : v)
			this.paths.add(p);
	
		return this;
	}
	
	public Script loadScript(CommonPath path) {
		for (int i = this.paths.size() - 1; i >= 0; i--) {
			Path path1 = this.paths.get(i).resolve(path.toString().substring(1));
			
			if (Files.exists(path1))
				return Script.of(path1);
		}
		
		ScriptResource parent = this.getParentResource();
		
		if (parent != null)
			return parent.loadScript(path);
		
		return null;
	}
	
	public Map<String, Class<? extends XElement>>  getParseMap() {
		Map<String, Class<? extends XElement>> tagmap = new HashMap<>();
		
		for (XElement config : tier.getConfig().getTagListDeep("Instructions/Tag")) {
			String name = config.getAttribute("Name");
			String cname = config.getAttribute("Class");
			
			if (StringUtil.isNotEmpty(name) && StringUtil.isNotEmpty(cname)) {
				Class<?> cclass = ResourceHub.getResources().getClassLoader().getClass(cname);
				
				if (cclass == null) {
					Logger.warn("Invalid script element: " + cname);
					continue;
				}
				
				tagmap.put(name, (Class<? extends XElement>) cclass);
			}
		}
		
		return tagmap;
	}
	
	/*
	public void registerTierDebugger(IDebuggerHandler v) {
		this.debugger = v;
	}
	
	public IDebuggerHandler getDebugger() {
		IDebuggerHandler db = this.debugger;
		
		if (db != null)
			return db;
		
		ScriptResource parent = this.getParentResource();

		if (parent != null)
			return parent.getDebugger();
		
		return null;
	}
	*/
	
	public void loadOperation(XElement config) {
		// TODO
	}

	public IOperator getOperation(String type, String op) {
		Map<String, IOperator> typeextensions = this.operationExtensions.get(type);
		
		if (typeextensions != null) {
			IOperator mut = typeextensions.get(op);
	
			if (mut != null) 
				return mut;
		}
		
		ScriptResource parent = this.getParentResource();

		if (parent != null)
			return parent.getOperation(type, op);
		
		return null;
	}
}
