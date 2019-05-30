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
import dcraft.script.work.StackWork;
import dcraft.stream.IStream;
import dcraft.stream.IStreamDown;
import dcraft.stream.IStreamUp;
import dcraft.struct.RecordStruct;
import dcraft.task.IParentAwareWork;
import dcraft.xml.XElement;

abstract public class BaseStream extends RecordStruct implements IStream {
	protected IStreamUp upstream = null;
	protected IStreamDown<?> downstream = null;
	
	@Override
	public void setUpstream(IStreamUp upstream) throws OperatingContextException {
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
	public void init(IParentAwareWork stack, XElement el) throws OperatingContextException {
	
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
