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

public enum ExecuteState {
    READY,      // instruction has been reset and ready for fresh run
    RESUME,       // been run at least once, ready to continue runs
    DONE
}
