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
package dcraft.hub.op;

// if you are listening to work, your efforts in "completed" will be in the work context, not yours - be sure to respect that
public interface IOperationObserver {
	void init(OperationContext ctx);
	ObserverState fireEvent(OperationContext ctx, OperationEvent event, Object detail);
}
