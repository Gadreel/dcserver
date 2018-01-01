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
package dcraft.script.work;

public enum ReturnOption {
	CONTINUE,		// only meaningful in a loop, otherwise results as a DONE (use AWAIT and task.resume if need to rerun work)
	AWAIT,
	CONTROL_BREAK,
	CONTROL_CONTINUE,
	DONE
}
