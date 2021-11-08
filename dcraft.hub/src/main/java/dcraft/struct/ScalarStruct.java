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
package dcraft.struct;

import dcraft.hub.op.OperatingContextException;
import dcraft.schema.DataType;
import dcraft.schema.RootType;
import dcraft.script.StackUtil;
import dcraft.script.inst.LogicBlockState;
import dcraft.task.IParentAwareWork;
import dcraft.xml.XElement;


abstract public class ScalarStruct extends BaseStruct implements Comparable {
	abstract public Object getGenericValue();
	abstract public void adaptValue(Object v);
	
	public ScalarStruct() {
	}
	
	public ScalarStruct(DataType type) {
		super(type);
	}

	// just a reminder of the things to override in types

	@Override
	abstract public boolean equals(Object obj);

	@Override
	abstract public int hashCode();
	
	// interior method - don't call unless you understand - TODO maybe move to Struct level...
	public boolean validateData(DataType type) {
		return true;
	}
	
	abstract public Object toInternalValue(RootType t);
	
	@Override
	abstract public int compareTo(Object o);
	
	public int compareToIgnoreCase(Object o) {
		return this.compareTo(o);
	}
	
	@Override
	public void checkLogic(IParentAwareWork stack, XElement source, LogicBlockState logicState) throws OperatingContextException {
		if (source.hasAttribute("Equal")) {
			if (logicState.pass) {
				BaseStruct other = StackUtil.refFromElement(stack, source, "Equal", true);
				logicState.pass = (this.compareTo(other) == 0);  //  (var == iv);
			}
			
			logicState.checked = true;
		}
		
		if (source.hasAttribute("Equals")) {
			if (logicState.pass) {
				BaseStruct other = StackUtil.refFromElement(stack, source, "Equals", true);
				logicState.pass = (this.compareTo(other) == 0);  //  (var == iv);
			}
			
			logicState.checked = true;
		}
		
		if (source.hasAttribute("LessThan")) {
			if (logicState.pass) {
				BaseStruct other = StackUtil.refFromElement(stack, source, "LessThan", true);
				logicState.pass = (this.compareTo(other) < 0);  //  (var < iv);
			}
			
			logicState.checked = true;
		}
		
		if (source.hasAttribute("GreaterThan")) {
			if (logicState.pass) {
				BaseStruct other = StackUtil.refFromElement(stack, source, "GreaterThan", true);
				logicState.pass = (this.compareTo(other) > 0);  //  (var > iv);
			}
			
			logicState.checked = true;
		}
		
		if (source.hasAttribute("LessThanOrEqual")) {
			if (logicState.pass) {
				BaseStruct other = StackUtil.refFromElement(stack, source, "LessThanOrEqual", true);
				logicState.pass = (this.compareTo(other) <= 0);  //  (var <= iv);
			}
			
			logicState.checked = true;
		}
		
		if (source.hasAttribute("GreaterThanOrEqual")) {
			if (logicState.pass) {
				BaseStruct other = StackUtil.refFromElement(stack, source, "GreaterThanOrEqual", true);
				logicState.pass = (this.compareTo(other) >= 0);  //  (var >= iv);
			}
			
			logicState.checked = true;
		}
		
		super.checkLogic(stack, source, logicState);
	}
}
