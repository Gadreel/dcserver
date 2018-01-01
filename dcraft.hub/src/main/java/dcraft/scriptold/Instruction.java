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

import dcraft.hub.op.OperatingContextException;
import dcraft.struct.RecordStruct;
import dcraft.xml.XElement;

abstract public class Instruction {
	protected XElement source = null;

    public XElement getXml() {
        return this.source; 
    }
    
    public void setXml(XElement v) { 
    	this.source = v; 
    }

    public boolean compile() {
    	return true;
    }
    
    abstract public void run(StackEntry stack) throws OperatingContextException;
	
    // override this if your instruction can cancel...
    abstract public void cancel(StackEntry stack);
    
	public RecordStruct collectDebugRecord(StackEntry stack, RecordStruct rec) {		
		rec.with("Line", this.source.getLine());
		rec.with("Column", this.source.getCol());
	   	rec.with("Command", this.source.toLocalString());
	   	
	   	return null;
	}

	public StackEntry createStack(Activity act, StackEntry parent) {
		return new StackEntry(act, parent, this);
	}	
}
