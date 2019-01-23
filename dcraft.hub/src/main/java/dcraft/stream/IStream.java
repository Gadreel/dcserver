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
package dcraft.stream;

import dcraft.hub.op.OperatingContextException;
import dcraft.scriptold.StackEntry;
import dcraft.xml.XElement;

public interface IStream {
	void init(StackEntry stack, XElement el) throws OperatingContextException;
	
	void setUpstream(IStreamUp upstream) throws OperatingContextException;
	IStreamUp getUpstream() throws OperatingContextException;
	
	void setDownstream(IStreamDown<?> downstream) throws OperatingContextException;
	IStreamDown<?> getDownstream() throws OperatingContextException;
	
	void cleanup() throws OperatingContextException;
}
