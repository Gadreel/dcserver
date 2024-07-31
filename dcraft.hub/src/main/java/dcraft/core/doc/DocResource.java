package dcraft.core.doc;

import dcraft.filestore.CommonPath;
import dcraft.hub.resource.IFileResolvingResource;
import dcraft.hub.resource.ResourceBase;
import dcraft.hub.resource.ResourceFileInfo;
import dcraft.hub.resource.ResourceTier;
import dcraft.log.Logger;
import dcraft.mail.CommResource;
import dcraft.script.Script;
import dcraft.util.StringUtil;
import dcraft.xml.XElement;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;

public class DocResource extends ResourceBase implements IFileResolvingResource {
	protected Path path = null;

	protected Map<String, XElement> dochandlers = new HashMap<>();

	public DocResource() {
		this.setName("Doc");
	}
	
	public DocResource getParentResource() {
		if (this.tier == null)
			return null;
		
		ResourceTier pt = this.tier.getParent();
		
		if (pt != null)
			return pt.getDoc();
		
		return null;
	}
	
	public DocResource withPath(Path v) {
		this.path = v;
		return this;
	}

	public void addDocHandler(XElement handler) {
		String name = handler.getAttribute("Name");

		if (StringUtil.isNotEmpty(name))
			this.dochandlers.put(name, handler);
	}

	public XElement getDocHandler(String name) {
		if (StringUtil.isNotEmpty(name))
			return this.dochandlers.get(name);

		return null;
	}

	public Set<String> getDocHandlers() {
		return this.dochandlers.keySet();
	}


	@Override
	public ResourceFileInfo findFile(CommonPath path) {
		if (this.path != null) {
			Path path1 = this.path.resolve(path.toString().substring(1));

			if (Files.exists(path1))
				return ResourceFileInfo.of(path, path1);
		}

		DocResource parent = this.getParentResource();

		if (parent != null)
			return parent.findFile(path);

		return null;
	}

	@Override
	public boolean hasPath(CommonPath path) {
		if (this.path != null) {
			Path path1 = this.path.resolve(path.toString().substring(1));

			if (Files.exists(path1))
				return true;
		}

		DocResource parent = this.getParentResource();

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
		if (this.path != null) {
			Path path1 = this.path.resolve(path.toString().substring(1));

			if (Files.exists(path1)) {
				for (String filename : filenames) {
					Path fpath = path1.resolve(filename);

					if (Files.exists(fpath) && Files.isRegularFile(fpath)) {
						results.add(ResourceFileInfo.of(path.resolve(filename), fpath));
						break;
					}
				}
			}
		}

		DocResource parent = this.getParentResource();

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
		if (this.path != null) {
			Path path1 = this.path.resolve(path.toString().substring(1));

			if (Files.exists(path1) && Files.isDirectory(path1)) {
				try {
					Files.walkFileTree(path1, EnumSet.of(FileVisitOption.FOLLOW_LINKS), 1,
							new SimpleFileVisitor<Path>() {
								@Override
								public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
										throws IOException {
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

		DocResource parent = this.getParentResource();

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
		if (this.path != null) {
			Path path1 = this.path.resolve(path.toString().substring(1));

			if (Files.exists(path1) && Files.isDirectory(path1)) {
				try {
					Files.walkFileTree(path1, EnumSet.of(FileVisitOption.FOLLOW_LINKS), 1,
							new SimpleFileVisitor<Path>() {
								@Override
								public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
										throws IOException {
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

		DocResource parent = this.getParentResource();

		// collect deep
		if (parent != null)
			parent.listFiles(path, results);

		return results;
	}
}
