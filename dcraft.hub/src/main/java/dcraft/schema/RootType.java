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
package dcraft.schema;

public enum RootType {
	String(1),
	Number(2),
	Boolean(3),
	Binary(4),
	Null(6),
	Any(7);
    
    private int code;

    private RootType(int c) {
      code = c;
    }

    public int getCode() {
      return code;
    }

	public static RootType parseCode(long code) {
		switch ((int)code) {
		case 1:
			return String;
		case 2:
			return Number;
		case 3:
			return Boolean;
		case 4:
			return Binary;
		case 6:
			return Null;
		default:
			return Any;
		}
	}    
}