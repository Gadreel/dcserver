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

import dcraft.util.StringUtil;

public class PathPart {
	public static PathPart[] parse(String path) {
		if (StringUtil.isEmpty(path))
			return null;
		
		String[] sparts = path.split(path.contains("/") ? "\\/" :  "\\.");
		PathPart[] parts = new PathPart[sparts.length];
		
		for (int i = 0; i < sparts.length; i++) {
			parts[i] = new PathPart(sparts[i]);
		}
		
		return parts;
	}
	
	protected String field = null;
	protected int index = 0;
	
	public PathPart(String field) {
		if (StringUtil.isDataInteger(field))
			this.index = (int)StringUtil.parseInt(field, 0);
		else
			this.field = field;
	}
	
	public PathPart(int index) {
		this.index = index;
	}
	
	public String getField() {
		return this.field;
	}
	
	public int getIndex() {
		return this.index;
	}
	
	public boolean isField() {
		return (this.field != null);
	}
}
