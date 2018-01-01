package dcraft.hub.resource;

import dcraft.filestore.CommonPath;
import dcraft.log.Logger;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class PackageResource extends ResourceBase {
	protected Path packagespath = null;
	
	protected List<Package> packages = new ArrayList<>();
	protected List<Package> reversepackages = new ArrayList<>();
	
	public PackageResource() {
		this.setName("Packages");
	}
	
	public List<Package> getTierList() {
		return this.packages;
	}
	
	public List<Package> getReverseTierList() {
		return this.reversepackages;
	}
	
	public Path getTierPath() {
		return this.packagespath;
	}
	
	// TODO someday replace with FileStore system so we always load through FS
	public void setTierPath(Path v) {
		this.packagespath = v;
	}
	
	public void addToTier(Package p) {
		this.packages.add(p);
		this.reversepackages.add(0, p);
	}
	
	public PackageResource getParentResource() {
		if (this.tier == null)
			return null;
		
		ResourceTier pt = this.tier.getParent();
		
		if (pt != null)
			return pt.getPackages();
		
		return null;
	}
	
	// return the path to the first file that matches, if any, in the package list
	public Path lookupPath(String path) {
		if (Logger.isDebug())
			Logger.debug("lookup path: " + path + " in " + (packages != null ? packages.size() : "[missing packages]"));
		
		for (Package rcomponent : this.reversepackages) {
			if (Logger.isDebug())
				Logger.debug("checking: " + path + " in " + rcomponent.getName());
			
			Path rpath = this.packagespath.resolve(rcomponent.getName() + path);
			
			if (Files.exists(rpath))
				return rpath;
		}
		
		PackageResource parent = this.getParentResource();
		
		if (parent != null)
			return parent.lookupPath(path);
		
		if (Logger.isDebug())
			Logger.debug("lookup path: " + path + " not found");
		
		return null;
	}
	
	public Path lookupPath(Path path) {
		if (path.isAbsolute())
			return null;
		
		for (Package rcomponent : this.reversepackages) {
			Path rpath = this.packagespath.resolve(rcomponent.getName()).resolve(path).normalize().toAbsolutePath();
			
			if (Files.exists(rpath))
				return rpath;
		}
		
		PackageResource parent = this.getParentResource();
		
		if (parent != null)
			return parent.lookupPath(path);
		
		return null;
	}
	
	public Path lookupPath(CommonPath path) {
		for (Package rcomponent : this.reversepackages) {
			Path rpath = this.packagespath.resolve(rcomponent.getName() + path);
			
			if (Files.exists(rpath))
				return rpath;
		}
		
		PackageResource parent = this.getParentResource();
		
		if (parent != null)
			return parent.lookupPath(path);
		
		return null;
	}
	
	/*
	// build a subset of the reverse package list from a list of package names
	// this will give the order in which to lookup stuff for package files
	public List<Package> buildLookupList(Collection<String> packagenames) {
		List<Package> res = new ArrayList<>();
		
		this.buildLookupList(packagenames, res);
		
		return res;
	}
	
	protected void buildLookupList(Collection<String> packagenames, List<Package> res) {
		PackageResource parent = this.getParentResource();
		
		if (parent != null)
			parent.buildLookupList(packagenames, res);
		
		for (Package pkg : this.reversepackages) {
			for (String pname : packagenames) {
				if (pkg.getName().equals(pname)) {
					res.add(pkg);
					break;
				}
			}
		}
	}
	*/
}
