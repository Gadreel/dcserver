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

import dcraft.hub.op.OperatingContextException;
import dcraft.struct.BaseStruct;
import dcraft.struct.Struct;
import dcraft.task.IParentAwareWork;
import dcraft.xml.XElement;

public interface IOperator {
	void operation(IParentAwareWork stack, XElement code, BaseStruct dest) throws OperatingContextException;
}
