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
package dcraft.scriptold;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

import dcraft.log.Logger;
import dcraft.script.ScriptHub;
import dcraft.util.StringUtil;
import dcraft.xml.XElement;

public class Script {
	static public final Pattern includepattern = Pattern.compile("(\\s*<\\?include\\s+\\/[A-Za-z0-9-_\\/]+\\.dcsl\\.xml\\s+\\?>\\s*\r?\n)", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
	
	protected XElement xml = null;
	protected Map<String,Instruction> functions = new HashMap<String,Instruction>();
	protected Instruction main = null;
	protected String source = null;

    public Script() {
    }

    public XElement getXml() {
        return this.xml; 
    }

    public Instruction getMain() {
    	return this.main;
    }

    public Instruction getFunction(String name) {
    	return this.functions.get(name);
    }
    
	public String getTitle() {
		if (this.xml == null)
			return null;
		
		XElement sc = this.xml.find("Script");
		
		return (sc != null) ? sc.getAttribute("Title") : "[Untitled]"; 
	}
	
	public String getSource() {
		return this.source;
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
        	*/
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
        */
        
        return true;
    }
}
