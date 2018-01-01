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
import dcraft.hub.op.OperationContext;
import dcraft.log.Logger;
import dcraft.struct.CompositeStruct;
import dcraft.struct.RecordStruct;
import dcraft.struct.Struct;
import dcraft.struct.scalar.IntegerStruct;
import dcraft.task.TaskContext;
import dcraft.util.StringUtil;

public class StackFunctionEntry extends StackBlockEntry {
	// result/state about the last command executed
    protected Struct lastResult = null;		
    protected IntegerStruct lastCode = null;		
    
    // parameter into function, make sure this is not disposed directly as the caller
    // does not want the param disposed
    protected Struct param = null;
    protected String pname = null;

    public StackFunctionEntry(Activity act, StackEntry parent, Instruction inst) {
		super(act, parent, inst);
		
        this.lastCode = IntegerStruct.of((long) 0);
    }

    @Override
    public Struct getLastResult() {
        return this.lastResult; 
    }
    
    @Override
    public void setLastResult(Struct v) throws OperatingContextException {
		this.lastResult = v;

		// if this is the Main function then the last result is also the task result
        if (this.parent == null) {
        	TaskContext run = OperationContext.getAsTaskOrThrow();
        	
        	if (run != null)
        		run.setResult(v);
        }
    }
    
    @Override
    public Long getLastCode() {
        return this.lastCode.getValue(); 
    }
    
    @Override
    public void setLastCode(Long v) {
    	// won't overwrite the existing code with 1
    	if ((v != null) && (v == 1) && (this.lastCode.getValue() > 1))
    		return;
    	
        this.lastCode.setValue(v); 
    }
    
    public Struct getParameter() {
        return this.param; 
    }
    
    public void setParameter(Struct v) {
        this.param = v; 
    }
    
    public String getParameterName() {
        return this.pname; 
    }
    
    public void setParameterName(String v) {
        this.pname = v; 
    }

	@Override
    public void collectDebugRecord(RecordStruct rec) {
		super.collectDebugRecord(rec);
		
    	RecordStruct dumpVariables = rec.getFieldAsRecord("Variables");
		
        if (StringUtil.isNotEmpty(this.pname)) 
        	dumpVariables.with(this.pname, (this.param != null) ? this.param : null);
        else
        	dumpVariables.with("_Param", (this.param != null) ? this.param : null);
        
        dumpVariables.with("_LastResult", this.lastResult);
        dumpVariables.with("_LastCode", this.lastCode);
    }	

	@Override
    public Struct queryVariable(String name) {
		if (StringUtil.isEmpty(name))
			return null;
		
		//if (name.equals(this.pname) || "_Param".equals(this.pname))
		//	return this.param;
		
        if ("_LastResult".equals(name) || "_".equals(name)) 
        	return this.lastResult;
        
        if ("_LastCode".equals(name) || "__".equals(name)) 
        	return this.lastCode;
        
        // needed to copy all of StackBlock here, except remove the query for parent vars - replace with check for global vars

        // do not call super - that would expose vars outside of the function
        int dotpos = name.indexOf(".");

        if (dotpos > -1) {
            String oname = name.substring(0, dotpos);

            Struct ov = null;
            
    		if (oname.equals(this.pname) || oname.equals("_Param"))
    			ov = this.param;
    		
    		if (ov == null)
    			ov = this.variables.containsKey(oname) ? this.variables.get(oname) : null;

            // support global variables
            if (ov == null) 
            	ov = this.activity.queryVariable(oname);

            if (ov == null) {
            	Logger.errorTr(515, oname);
            	return null;
            }
            
            if (!(ov instanceof CompositeStruct)){
            	Logger.errorTr(516, oname);
            	return null;
            }
            
            return ((CompositeStruct)ov).select(name.substring(dotpos + 1)); 
        }
        else if (name.equals(this.pname) || name.equals("_Param")) {
            return this.param;
        }
        else if (this.variables.containsKey(name)) {
            return this.variables.get(name);
        }
        
        // if nothing else, try globals
        return this.activity.queryVariable(name);
    }
	
	@Override
	public StackFunctionEntry queryFunctionStack() {
		return this;
	}
}
