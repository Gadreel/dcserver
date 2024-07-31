package dcraft.script;

import dcraft.filestore.CommonPath;
import dcraft.hub.ResourceHub;
import dcraft.hub.resource.IFileResolvingResource;
import dcraft.hub.resource.ResourceBase;
import dcraft.hub.resource.ResourceFileInfo;
import dcraft.hub.resource.ResourceTier;
import dcraft.log.Logger;
import dcraft.script.mutator.Substring;
import dcraft.struct.format.IFormatter;
import dcraft.util.FileUtil;
import dcraft.util.IOUtil;
import dcraft.util.StringUtil;
import dcraft.xml.XElement;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;

public class ScriptResource extends ResourceBase implements IFileResolvingResource {
	protected Map<String, Class<? extends XElement>> tagmap = new HashMap<>();

	// operator is type specific - [Type][Name] = mutator
	protected Map<String, Map<String, IOperator>> operationExtensions = new HashMap<>();
	protected List<Path> paths = new ArrayList<>();
	
	protected Map<String, XElement> formatterclasses = new HashMap<>();
	//cached
	protected Map<String, IFormatter> formatters = new HashMap<>();
	
	// TODO someday restore - protected IDebuggerHandler debugger = null;

	public ScriptResource() {
		this.setName("Script");
		
		// TODO move and load from packages/settings
		HashMap<String, IOperator> stringextensions = new HashMap<String, IOperator>();
		// TODO stringextensions.put("Substring", new Substring());
		
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

	public Path findScript(CommonPath path) {
		ResourceFileInfo info = this.findFile(path);

		return  (info != null) ? info.getActualPath() : null;
	}

	@Override
	public ResourceFileInfo findFile(CommonPath path) {
		for (int i = this.paths.size() - 1; i >= 0; i--) {
			Path path1 = this.paths.get(i).resolve(path.toString().substring(1));

			if (Files.exists(path1))
				return ResourceFileInfo.of(path, path1);
		}

		ScriptResource parent = this.getParentResource();

		if (parent != null)
			return parent.findFile(path);

		return null;
	}

	@Override
	public boolean hasPath(CommonPath path) {
		for (int i = this.paths.size() - 1; i >= 0; i--) {
			Path path1 = this.paths.get(i).resolve(path.toString().substring(1));

			if (Files.exists(path1))
				return true;
		}

		ScriptResource parent = this.getParentResource();

		if (parent != null)
			return parent.hasPath(path);

		return false;
	}

	@Override
	public List<ResourceFileInfo> findFiles(CommonPath path, List<String> filenames) {
		List<ResourceFileInfo> results = new ArrayList<>();

		this.findFiles(path, filenames, results);

		return results;
	}

	// resolve only once per file per tier
	protected List<ResourceFileInfo> findFiles(CommonPath path, List<String> filenames, List<ResourceFileInfo> results) {
		boolean tierfnd = false;

		for (int i = this.paths.size() - 1; i >= 0; i--) {
			Path path1 = this.paths.get(i).resolve(path.toString().substring(1));

			if (Files.exists(path1)) {
				for (String filename : filenames) {
					Path fpath = path1.resolve(filename);

					if (Files.exists(fpath) && Files.isRegularFile(fpath)) {
						results.add(ResourceFileInfo.of(path.resolve(filename), fpath));
						tierfnd = true;
						break;
					}
				}
			}

			if (tierfnd)
				break;
		}

		ScriptResource parent = this.getParentResource();

		// collect deep
		if (parent != null)
			parent.findFiles(path, filenames, results);

		return results;
	}

	@Override
	public List<ResourceFileInfo> findSubFolders(CommonPath path) {
		List<ResourceFileInfo> results = new ArrayList<>();

		this.findSubFolders(path, results);

		return results;
	}

	protected List<ResourceFileInfo> findSubFolders(CommonPath path, List<ResourceFileInfo> results) {
		for (int i = this.paths.size() - 1; i >= 0; i--) {
			Path path1 = this.paths.get(i).resolve(path.toString().substring(1));

			if (Files.exists(path1) && Files.isDirectory(path1)) {
				try {
					Files.walkFileTree(path1, EnumSet.of(FileVisitOption.FOLLOW_LINKS), 1,
							new SimpleFileVisitor<Path>() {
								@Override
								public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
										throws IOException
								{
									if (Files.isDirectory(file))
										results.add(ResourceFileInfo.of(path.resolve(file.getFileName().toString()), file));

									return FileVisitResult.CONTINUE;
								}
							});
				}
				catch (IOException x) {
					Logger.error("Error finding : " + x);
				}
			}
		}

		ScriptResource parent = this.getParentResource();

		// collect deep
		if (parent != null)
			parent.findSubFolders(path, results);

		return results;
	}

	@Override
	public List<ResourceFileInfo> listFiles(CommonPath path) {
		List<ResourceFileInfo> results = new ArrayList<>();

		this.listFiles(path, results);

		return results;
	}

	protected List<ResourceFileInfo> listFiles(CommonPath path, List<ResourceFileInfo> results) {
		for (int i = this.paths.size() - 1; i >= 0; i--) {
			Path path1 = this.paths.get(i).resolve(path.toString().substring(1));

			if (Files.exists(path1) && Files.isDirectory(path1)) {
				try {
					Files.walkFileTree(path1, EnumSet.of(FileVisitOption.FOLLOW_LINKS), 1,
							new SimpleFileVisitor<Path>() {
								@Override
								public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
										throws IOException
								{
									if (Files.isRegularFile(file))
										results.add(ResourceFileInfo.of(path.resolve(file.getFileName().toString()), file));

									return FileVisitResult.CONTINUE;
								}
							});
				}
				catch (IOException x) {
					Logger.error("Error finding : " + x);
				}
			}
		}

		ScriptResource parent = this.getParentResource();

		// collect deep
		if (parent != null)
			parent.listFiles(path, results);

		return results;
	}

	// this means we require a server restart when a new instruction is added
	public Map<String, Class<? extends XElement>>  getParseMap() {
		if (this.tagmap.isEmpty()) {
			for (XElement config : ResourceHub.getResources().getConfig().getTagListDeep("Instructions/Tag")) {
				String name = config.getAttribute("Name");
				String cname = config.getAttribute("Class");

				if (StringUtil.isNotEmpty(name) && StringUtil.isNotEmpty(cname)) {
					Class<?> cclass = ResourceHub.getResources().getClassLoader().getClass(cname);

					if (cclass == null) {
						Logger.warn("Invalid script element: " + cname);
						continue;
					}

					this.tagmap.put(name, (Class<? extends XElement>) cclass);
				}
			}
		}
		
		return this.tagmap;
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
	
	public void loadFormatter(XElement config) {
		if ((config != null) && config.hasNotEmptyAttribute("Code")) {
			// only place in "classes" as the JARs for this site/tenant are not yet loaded
			this.formatters.remove(config.getAttribute("Code"));
			this.formatterclasses.put(config.getAttribute("Code"), config);
		}
	}
	
	public IFormatter getFormatter(String op) {
		if (StringUtil.isEmpty(op))
			return null;
			
		IFormatter formatter = this.formatters.get(op);
		
		if (formatter != null)
			return formatter;
		
		XElement config = this.formatterclasses.get(op);
		
		if (config != null) {
			String cname = config.getAttribute("Class");
			
			if (StringUtil.isNotEmpty(cname)) {
				try {
					IFormatter fi = (IFormatter) ResourceHub.getTopResources().getClassLoader().getInstance(cname);
					
					this.formatters.put(op, fi);
					
					return fi;
				}
				catch (Exception x) {
					Logger.error("Bad Formatter Class");
				}
			}
		}
		
		ScriptResource parent = this.getParentResource();
		
		if (parent != null)
			return parent.getFormatter(op);
		
		return null;
	}
	
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
