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
import dcraft.stream.IStream;
import dcraft.stream.IStreamDown;
import dcraft.stream.IStreamUp;
import dcraft.struct.RecordStruct;
import dcraft.xml.XElement;

abstract public class BaseStream extends RecordStruct implements IStream {
	protected IStreamUp upstream = null;
	protected IStreamDown<?> downstream = null;
	
	@Override
	public void setUpstream(IStreamUp upstream) {
		this.upstream = upstream;
		
		upstream.setDownstream((IStreamDown<?>) this);
	}

	@Override
	public IStreamUp getUpstream() {
		return this.upstream;
	}
	
	@Override
	public void setDownstream(IStreamDown<?> downstream) {
		this.downstream = downstream;
	}
	
	@Override
	public IStreamDown<?> getDownstream() {
		return this.downstream;
	}
	
	@Override
	public void init(StackEntry stack, XElement el) {
		// NA
	}
	
	@Override
	public void cleanup() throws OperatingContextException {
		IStreamUp up = this.upstream;
		
		if (up != null)
			up.cleanup();
		
		this.close();
	}
	
	public void close() throws OperatingContextException {
		this.downstream = null;
		this.upstream = null;
	}
}
