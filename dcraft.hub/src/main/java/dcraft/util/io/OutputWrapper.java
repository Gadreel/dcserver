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
package dcraft.util.io;

import java.io.IOException;
import java.io.OutputStream;

import dcraft.util.Memory;

public class OutputWrapper extends OutputStream {
	protected Memory mem = null;
	
	public OutputWrapper() {
		this.mem = new Memory();
	}
	
	public OutputWrapper(Memory mem) {
		this.mem = mem;
	}

	public Memory getMemory() {
		return this.mem;
	}

	@Override
	public void write(int value) throws IOException {
		this.mem.writeByte((byte)value);
	}
}
