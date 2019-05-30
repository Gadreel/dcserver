/* ************************************************************************
#
#  designCraft.io
#
#  http://designcraft.io/
#
#  Copyright:
#    Copyright 2014 eTimeline, LLC. All rights reserved.
#
#  License:
#    See the license.txt file in the project's top-level directory for details.
#
#  Authors:
#    * Andy White
#
************************************************************************ */
package dcraft.script;

import dcraft.filestore.CommonPath;
import dcraft.hub.ResourceHub;
import dcraft.log.Logger;
import dcraft.script.inst.Instruction;
import dcraft.script.inst.doc.Base;
import dcraft.task.IWork;
import dcraft.task.IWorkBuilder;
import dcraft.util.IOUtil;
import dcraft.util.StringUtil;
import dcraft.xml.XElement;
import dcraft.xml.XmlReader;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

public class Script implements IWorkBuilder {
	static public final Pattern includepattern = Pattern.compile("(\\s*<\\?include\\s+\\/[A-Za-z0-9-_\\/]+\\.dcsl\\.xml\\s+\\?>\\s*\r?\n)", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
	
	static public Script of(CommonPath path) {
		return ResourceHub.getResources().getScripts().loadScript(path);
	}

	static public Script of(Path path) {
		try {
			CharSequence src = IOUtil.readEntireFile(path);
			
			XElement xml = XmlReader.parse(src, true, true, ResourceHub.getResources().getScripts().getParseMap(), Base.class);
			
			if (xml != null)
				return Script.of(xml, src);
			
			Logger.error("Error loading script file: " + path + ", unable to parse.");
		}
		catch (Exception x) {
			Logger.error("Error loading script file: " + path + ", error: " + x);
		}
		
		return null;
	}
	
	static public Script of(CharSequence src) {
		XElement xml = XmlReader.parse(src, true, true, ResourceHub.getResources().getScripts().getParseMap(), Base.class);
		
		if (xml != null)
			return Script.of(xml, src);
		
		return null;
	}
	
	static public Script of(XElement doc, CharSequence src) {
		if (doc == null) {
			Logger.error(1, "No script document provided, cannot compile.");
			return null;
		}
		
		if (! (doc instanceof Instruction)) {
			Logger.error(1, "Script document must contain Instruction root.");
			return null;
		}
		
		Script script = new Script();
		
		script.xml = (Instruction) doc;
		script.source = src;
		
		/* TODO rework - functions self declare
		for (XNode node : doc.getChildren()) {
			if (node instanceof Main)
				script.main = (Main) node;
			else if (node instanceof Function)
				script.functions.put(((Function)node).getAttribute("Name"), (Function)node);
		}
		*/
		
		return script;
	}
	
	protected CharSequence source = null;
	protected Instruction xml = null;
	/* TODO rework
	protected Map<String,Instruction> functions = new HashMap<>();
	protected Main main = null;
	*/

    protected Script() { }

    public Instruction getXml() {
        return this.xml; 
    }

	/* TODO rework
    public Instruction getMain() {
    	return this.main;
    }

    public Instruction getFunction(String name) {
    	return this.functions.get(name);
    }
    */
    
	public String getTitle() {
		if (this.xml == null)
			return null;
		
		return this.xml.getAttribute("Title");
	}
	
	public CharSequence getSource() {
		return this.source;
	}
	
	/*
	public Instruction getMain() {
		return this.main;
	}
	
	public Instruction getFunction(String name) {
		return this.functions.get(name);
	}
	
	public boolean compile(XElement doc, String src) {
		this.xml = doc;
		this.source = src;
		this.main = null;
		this.functions.clear();
		
		if (doc == null) {
			Logger.error(1, "No scriptold document provided, cannot compile.");
			return false;
		}
		
		for (XElement func : doc.selectAll("Function")) {
			String fname = func.getAttribute("Name");
			
			if (StringUtil.isEmpty(fname))
				continue;
        	
        	/* cleaning up ---
	        Instruction ni = ScriptHub.createInstruction(func);
	        ni.setXml(func);
	        
	        if (! ni.compile())
	        	return false;
	        
        	this.functions.put(fname, ni);
        	* /
		}
		
		XElement node = doc.find("Main");
		
		if (node == null) {
			Logger.errorTr(506);
			return false;
		}

        	/* cleaning up ---
        Instruction ni = ScriptHub.createInstruction(node);
        ni.setXml(node);
        
        if (! ni.compile())
        	return false;

        this.main = ni;
        * /
		
		return true;
	}
	*/

	@Override
	public IWork toWork() {
		return this.xml.createStack(null);
	}
	
	public IWork toWorkMain() throws Exception {
		return this.xml.createStackMain(null);
	}
}
