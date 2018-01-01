package dcraft.hub.resource;

import java.nio.file.Path;

import dcraft.xml.XElement;

public class Package {
	static public Package of(String name, XElement def, Path path) {
		Package p = new Package();
		p.name = name;
		p.definition = def;
		p.path = path;
		return p;
	}
	
	protected Path path = null;
	protected XElement definition = null;
	protected String name = null;

	public String getName() {
		return this.name;
	}
	
	public XElement getDefinition() {
		return this.definition;
	}
	
	public Path getPath() {
		return this.path;
	}
}
