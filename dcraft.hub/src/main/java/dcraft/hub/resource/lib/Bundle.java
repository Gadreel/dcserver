/* ************************************************************************
#
#  DivConq
#
#  http://divconq.com/
#
#  Copyright:
#    Copyright 2012 eTimeline, LLC. All rights reserved.
#
#  License:
#    See the license.txt file in the project's top-level directory for details.
#
#  Authors:
#    * Andy White
#
************************************************************************ */
package dcraft.hub.resource.lib;

import dcraft.hub.app.ApplicationHub;
import dcraft.log.Logger;
import dcraft.xml.XElement;
import dcraft.xml.XmlReader;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.lang.ClassLoader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import javax.tools.JavaFileObject;

public class Bundle extends ClassLoader {
	protected List<LibLoader> libloaders = new ArrayList<LibLoader>();
	
	public Bundle(ClassLoader parent) {
		super(parent);
	}	
	
	public void loadJarLibrary(Path path) {
		if ((path != null) && path.getFileName().toString().endsWith(".jar"))
			this.libloaders.add(new JarLibLoader(path));
	}
	
	/* load folder of classes
			// TODO else
			// TODO	lib = new FolderLibLoader(path);
			//this.libloaders.add(lib);
	 */

	/*
	public Object getInstance(String cname) {
		try {
			return this.getClass(cname).newInstance();
		} 
		catch (Exception x) {
			//System.out.println("err: " + x);
		}
		
		return null;
	}
	*/
	
	public XElement getResourceAsXml(String name, boolean keepwhitespace) {
		try {
			InputStream is = this.getResourceAsStream(name);
			
			if (is == null) {
				Logger.errorTr(133, name);
				return null;
			}
			
			return XmlReader.parse(is, keepwhitespace, true);
		} 
		catch (Exception x) {
			Logger.errorTr(134, name, x);
			return null;
		}
	}

	/*
	public Class<?> getClass(String cname) {
		try {
			return Class.forName(cname, true, this);
		} 
		catch (Exception x) {
		}
		
		return null;
	}
	*/
	
	public byte[] findClassEntry(String name) {
		return this.findFileEntry("/" + name.replace(".", "/") + ".class");
	}
	
	public Iterable<JavaFileObject> listPackageClasses(String packname) {
		Map<String, JavaFileObject> files = new HashMap<String, JavaFileObject>();
		
		packname = "/" + packname.replace(".", "/");
		
		for (int i = this.libloaders.size() - 1; i >= 0; i--) {
			LibLoader lib = this.libloaders.get(i);
			
			for (Entry<String, byte[]> pentry : lib.getEntries()) {
				String name = pentry.getKey();
				
				if (name.startsWith(packname) && name.endsWith(".class"))
					files.put(name, new BundleFile(name, pentry.getValue()));
			}
		}	
		
		// TODO list parent too if parent is a Bundle
		
		return files.values();
	}
	
	public byte[] findFileEntry(String name) {
		for (LibLoader lib : this.libloaders) {
			byte[] cd = lib.getEntry(name);
			
			if (cd != null)
				return cd;
		}		
		
		ClassLoader p = this.getParent();
		
		if (p instanceof Bundle)
			return ((Bundle)p).findFileEntry(name);
		
		return null;
	}

	public boolean hasFileEntry(String fpath) {
		for (LibLoader lib : this.libloaders) 
			if (lib.hasEntry(fpath))
				return true;
		
		ClassLoader p = this.getParent();
		
		if (p instanceof Bundle)
			return ((Bundle)p).hasFileEntry(fpath);
		
		return false;
	}
	
	@Override
	protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
		// TODO this can be enhanced to cache the class definitions
		byte[] cd = this.findClassEntry(name);
		
		if (cd != null)
			return super.defineClass(name, cd, 0, cd.length);
		
		return super.loadClass(name, resolve);
	}
	
	/*
	@Override
	protected Class<?> findClass(String name) throws ClassNotFoundException {
		byte[] cd = this.findClassEntry(name);
		
		if (cd != null) 
			return super.defineClass(name, cd, 0, cd.length);
		
		return super.findClass(name);
	}
	*/
	
	@Override
	public InputStream getResourceAsStream(String name) {
		byte[] entry = this.findFileEntry(name);
		
		if (entry == null)
			return null;
		
		return new ByteArrayInputStream(entry);
	}
	
}
