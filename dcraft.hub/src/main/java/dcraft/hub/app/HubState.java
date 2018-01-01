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
package dcraft.hub.app;

/*
 * Valid state transitions
 * 
 *  Booting		->		Booted | Stopping
 *  Booted 		->		Running | Stopping
 *  Running		->		Idle | Booted | Stopping		
 *  Idle		->		Running | Booted | Stopping		
 *  Stopping	->		Stopped
 * 
 */
public enum HubState {
	Booting(0),
	Booted(1),				// local/base config loaded and started
	Running(3),				// extra/db config loaded and started - full operations may now kick in
	Idle(4),				// extra/db config loaded and started - do not run scheduler, or accept new logins or new uploads - this hub is winding down or going to pause
	Stopping(5),
	Stopped(6);
    
    protected int code;

    private HubState(int c) {
      this.code = c;
    }

    public int getCode() {
      return this.code;
    }
}

