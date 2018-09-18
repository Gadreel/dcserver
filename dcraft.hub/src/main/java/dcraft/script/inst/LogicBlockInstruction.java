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
package dcraft.script.inst;

import dcraft.hub.op.OperatingContextException;
import dcraft.script.StackUtil;
import dcraft.script.work.InstructionWork;
import dcraft.struct.ScalarStruct;
import dcraft.struct.Struct;
import dcraft.xml.XElement;

abstract public class LogicBlockInstruction extends BlockInstruction {
    protected boolean checkLogic(InstructionWork stack) throws OperatingContextException {
        return checkLogic(stack, this);
    }

    protected boolean checkLogic(InstructionWork stack, XElement source) throws OperatingContextException {
        if (source == null) 
        	source = this;
      
        Struct target = source.hasAttribute("Target")
        		? StackUtil.refFromElement(stack, source, "Target", true)
        	    : StackUtil.queryVariable(stack, "_LastResult");

        return LogicBlockInstruction.checkLogic(stack, target, source);
    }

    static public boolean checkLogic(InstructionWork stack, Struct target, XElement source) throws OperatingContextException {
        boolean isok = true;
		boolean condFound = false;

        if (target == null) {
        	isok = false;
            
    		if (StackUtil.boolFromElement(stack, source, "IsNull") || StackUtil.boolFromElement(stack, source, "IsEmpty"))
    			isok = ! isok;
        }
        else {
        	if (target instanceof ScalarStruct) {
        		ScalarStruct starget = (ScalarStruct) target;

				if (!condFound && source.hasAttribute("Equal")) {
					Struct other = StackUtil.refFromElement(stack, source, "Equal", true);
					isok = (starget.compareTo(other) == 0);  //  (var == iv);
					condFound = true;
				}

				if (!condFound && source.hasAttribute("Equals")) {
					Struct other = StackUtil.refFromElement(stack, source, "Equals", true);
					isok = (starget.compareTo(other) == 0);  //  (var == iv);
					condFound = true;
				}

				if (!condFound && source.hasAttribute("LessThan")) {
					Struct other = StackUtil.refFromElement(stack, source, "LessThan", true);
					isok = (starget.compareTo(other) < 0);  //  (var < iv);
					condFound = true;
				}

				if (!condFound && source.hasAttribute("GreaterThan")) {
					Struct other = StackUtil.refFromElement(stack, source, "GreaterThan", true);
					isok = (starget.compareTo(other) > 0);  //  (var > iv);
					condFound = true;
				}

				if (!condFound && source.hasAttribute("LessThanOrEqual")) {
					Struct other = StackUtil.refFromElement(stack, source, "LessThanOrEqual", true);
					isok = (starget.compareTo(other) <= 0);  //  (var <= iv);
					condFound = true;
				}

				if (!condFound && source.hasAttribute("GreaterThanOrEqual")) {
					Struct other = StackUtil.refFromElement(stack, source, "GreaterThanOrEqual", true);
					isok = (starget.compareTo(other) >= 0);  //  (var >= iv);
					condFound = true;
				}
			}

			if (! condFound && source.hasAttribute("IsNull")) {
				isok = StackUtil.boolFromElement(stack, source, "IsNull") ? target.isNull() : !target.isNull();
				condFound = true;
			}
			
			if (! condFound && source.hasAttribute("IsEmpty")) {
				isok = StackUtil.boolFromElement(stack, source, "IsEmpty") ? target.isEmpty() : !target.isEmpty();
				condFound = true;
			}

			if (! condFound)
				isok = target.checkLogic(stack, source);
        }
        
		if (StackUtil.boolFromElement(stack, source, "Not"))
			isok = !isok;

        return isok;
    }
}
