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
import dcraft.struct.BaseStruct;
import dcraft.struct.ScalarStruct;
import dcraft.struct.Struct;
import dcraft.struct.scalar.NullStruct;
import dcraft.xml.XElement;
import dcraft.xml.XNode;

abstract public class LogicBlockInstruction extends BlockInstruction {
    protected boolean checkLogic(InstructionWork stack) throws OperatingContextException {
        return checkLogic(stack, this);
    }

    protected boolean checkLogic(InstructionWork stack, XElement source) throws OperatingContextException {
        if (source == null) 
        	source = this;

        BaseStruct target = source.hasAttribute("Target")
        		? StackUtil.refFromElement(stack, source, "Target", true)
        	    : StackUtil.queryVariable(stack, "_LastResult");

        return LogicBlockInstruction.checkLogic(stack, target, source);
    }

    static public boolean checkLogic(InstructionWork stack, BaseStruct target, XElement source) throws OperatingContextException {
		LogicBlockState logicState = new LogicBlockState();

        if (target == null)
        	target = NullStruct.instance;

		target.checkLogic(stack, source, logicState);

        if (LogicBlockInstruction.isChildrenLogic(source)) {
            logicState.checked = true;   // if there are any children then treat it as checked, even if the children are empty

            if (logicState.pass)
                LogicBlockInstruction.checkChildrenLogic(stack, target, source, logicState, ScriptLogicModeEnum.AND);
        }

        // if there were no conditions checked then consider the value of Target for trueness
        if (!logicState.checked)
            logicState.pass = Struct.objectToBooleanOrFalse(target);

        if (StackUtil.boolFromElement(stack, source, "Not"))
            logicState.pass = !logicState.pass;

        return logicState.pass;
    }

    static public boolean isChildrenLogic(XElement source) throws OperatingContextException {
        for (int i = 0; i < source.getChildCount(); i++) {
            XNode node = source.getChild(i);

            if (node instanceof XElement) {
                XElement element = (XElement) node;

                switch (element.getName()) {
                    case "And":
                    case "Or":
                    case "Is":
                        return true;
                }
            }
        }

        return false;
    }

    static public void checkChildrenLogic(InstructionWork stack, BaseStruct target, XElement source, LogicBlockState logicState, ScriptLogicModeEnum mode) throws OperatingContextException {
        for (int i = 0; i < source.getChildCount(); i++) {
            XNode node = source.getChild(i);

            if (node instanceof XElement) {
                XElement element = (XElement) node;

                BaseStruct localtarget = element.hasAttribute("Target")
                        ? StackUtil.refFromElement(stack, element, "Target", true)
                        : target;

                if (localtarget == null)
                    localtarget = NullStruct.instance;

                boolean logicElement = false;

                switch (element.getName()) {
                    case "And":
                        logicElement = true;

                        LogicBlockInstruction.checkChildrenLogic(stack, localtarget, element, logicState, ScriptLogicModeEnum.AND);

                        break;
                    case "Or":
                        logicElement = true;

                        LogicBlockInstruction.checkChildrenLogic(stack, localtarget, element, logicState, ScriptLogicModeEnum.OR);

                        break;
                    case "Is":
                        logicElement = true;

                        LogicBlockState tempLogicState = new LogicBlockState();

                        localtarget.checkLogic(stack, element, tempLogicState);

                        if (! tempLogicState.checked) {
                            logicState.pass = Struct.objectToBooleanOrFalse(localtarget);
                        }
                        else {
                            logicState.pass = tempLogicState.pass;
                        }

                        break;
                }

                if (logicElement) {
                    if (StackUtil.boolFromElement(stack, element, "Not"))
                        logicState.pass = ! logicState.pass;

                    // see if we can short circuit the logic

                    if ((mode == LogicBlockInstruction.ScriptLogicModeEnum.AND) && ! logicState.pass)
                        return;

                    if ((mode == LogicBlockInstruction.ScriptLogicModeEnum.OR) && logicState.pass)
                        return;
                }
            }
        }
    }

    public enum ScriptLogicModeEnum {
        AND, OR
    }
}
