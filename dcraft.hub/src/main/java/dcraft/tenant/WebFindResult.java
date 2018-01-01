package dcraft.tenant;

import dcraft.filestore.CommonPath;

import java.nio.file.Path;

public class WebFindResult {
	static public WebFindResult of(Path file, CommonPath path) {
		WebFindResult res = new WebFindResult();
		res.file = file;
		res.path = path;
		return res;
	}
	
	public Path file = null;
	public CommonPath path = null;
}
