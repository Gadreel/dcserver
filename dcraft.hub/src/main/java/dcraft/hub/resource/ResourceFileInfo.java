package dcraft.hub.resource;

import dcraft.filestore.CommonPath;

import java.nio.file.Files;
import java.nio.file.Path;

public class ResourceFileInfo {
	static public ResourceFileInfo of(CommonPath logicalPath, Path actualPath) {
		ResourceFileInfo info = new ResourceFileInfo();
		info.actualPath = actualPath;
		info.logicalPath = logicalPath;
		return info;
	}

	protected CommonPath logicalPath = null;
	protected Path actualPath = null;

	public CommonPath getLogicalPath() {
		return this.logicalPath;
	}

	public Path getActualPath() {
		return this.actualPath;
	}

	public boolean isFolder() {
		return Files.isDirectory(this.actualPath);
	}
}
