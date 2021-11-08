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
package dcraft.script.mutator;

import dcraft.hub.op.OperatingContextException;
import dcraft.script.IOperator;
import dcraft.script.StackUtil;
import dcraft.struct.BaseStruct;
import dcraft.struct.Struct;
import dcraft.struct.scalar.StringStruct;
import dcraft.task.IParentAwareWork;
import dcraft.util.StringUtil;
import dcraft.xml.XElement;

public class Substring implements IOperator {

	@Override
	public void operation(IParentAwareWork stack, XElement code, BaseStruct dest) throws OperatingContextException {
		int from = (int) StackUtil.intFromElement(stack, code, "From", 0);
		int to = (int) StackUtil.intFromElement(stack, code, "To", 0);
		int length = (int) StackUtil.intFromElement(stack, code, "Length", 0);
				
		if (dest instanceof StringStruct) {
			StringStruct idest = (StringStruct)dest;
			String val = idest.getValueAsString();
			
			if (StringUtil.isEmpty(val))
				return;
			
			if (to > 0) 
				idest.setValue(val.substring(from, to));
			else if (length > 0) 
				idest.setValue(val.substring(from, from + length));
			else
				idest.setValue(val.substring(from));
			
			System.out.println("Using override Substring!");
		}
		
		// TODO review stack.resume();
	}
}
