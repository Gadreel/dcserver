package dcraft.hub.resource;

import dcraft.filestore.CommonPath;

import java.util.List;

public interface IFileResolvingResource {
	// find first (top) level match of exact name
	ResourceFileInfo findFile(CommonPath path);

	// find either of these files at each level, return top first
	List<ResourceFileInfo> findFiles(CommonPath path, List<String> filenames);

	// check to see if the file or folder exists at all
	boolean hasPath(CommonPath path);

	// get all possible files in folder, including those deeper
	List<ResourceFileInfo> listFiles(CommonPath path);

	// get all possible subfolders, but in Common format, for later use in file lookups
	// note the list does not repeat the same path name, even if found at different levels
	List<ResourceFileInfo> findSubFolders(CommonPath path);
}
