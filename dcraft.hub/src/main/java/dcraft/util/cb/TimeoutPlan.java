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
package dcraft.util.cb;

public enum TimeoutPlan {
	Regular,
	Long,
	ExtraLong;
	
	public int getSeconds() {
		// 2 minutes is regular    TODO config
		
		if (this == TimeoutPlan.Long) 
			return 300;
		
		if (this == TimeoutPlan.ExtraLong)
			return 1200;
		
		return 120;
	}
}
