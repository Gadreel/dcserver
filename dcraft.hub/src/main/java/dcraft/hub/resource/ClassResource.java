package dcraft.hub.resource;

import dcraft.filestore.CommonPath;
import dcraft.hub.ResourceHub;
import dcraft.hub.app.ApplicationHub;
import dcraft.hub.resource.lib.Bundle;
import dcraft.log.Logger;
import dcraft.struct.Struct;
import dcraft.util.FileUtil;
import dcraft.util.MimeInfo;
import dcraft.util.StringUtil;
import dcraft.xml.XElement;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public class ClassResource extends ResourceBase {
	protected Bundle bundle = null;
	
	public ClassResource() {
		this.setName("Class");
	}
	
	public void setBundle(Bundle v) {
		this.bundle = v;
	}
	
	public ClassResource getParentResource() {
		if (this.tier == null)
			return null;
		
		ResourceTier pt = this.tier.getParent();
		
		if (pt != null)
			return pt.getClassLoader();
		
		return null;
	}
	
	public void load(XElement config) {
	}

	public ClassLoader getClassLoader() {
		if (this.bundle != null)
			return this.bundle;			// class loader naturally cascades so no need to check parent
		
		ClassResource p = this.getParentResource();
		
		if (p != null)
			return p.getClassLoader();
		
		return this.getClass().getClassLoader();
	}
	
	public Object getInstance(String cname) {
		try {
			return this.getClass(cname).newInstance();
		}
		catch (Exception x) {
			Logger.error("Unable to create instance for: " + cname);
		}
		
		return null;
	}
	
	public Class<?> getClass(String cname) {
		try {
			ClassLoader cl = this.getClassLoader();		// class loader naturally cascades so no need to check parent
			
			return cl.loadClass(cname);
		}
		catch (Exception x) {
			Logger.error("Unable to load class: " + cname);
		}
		
		return null;
	}
}
