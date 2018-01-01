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
package dcraft.stream.record;

import dcraft.hub.op.OperatingContextException;
import dcraft.scriptold.StackEntry;
import dcraft.stream.IStreamSource;
import dcraft.stream.ReturnOption;
import dcraft.struct.ListStruct;
import dcraft.struct.RecordStruct;
import dcraft.xml.XElement;

public class ListSourceStream extends BaseRecordStream implements IStreamSource, IRecordStreamSupplier {
	static public ListSourceStream of(ListStruct src) {
		ListSourceStream strm = new ListSourceStream();
		strm.source = src;
		return strm;
	}
	
	protected ListStruct source = null;	
	protected int current = 0;
	
	protected ListSourceStream() {
	};
	
	@Override
	public void init(StackEntry stack, XElement el) {
	}
	
	@Override
	public void close() throws OperatingContextException {
		this.source = null;
		
		super.close();
	}

	/**
	 * Someone downstream wants more data
	 */
	@Override
	public void read() throws OperatingContextException {
		if (this.source == null) {
			this.consumer.handle(null);
			return;
		}
		
		while (this.source.getSize() > this.current) {
			RecordStruct rec = this.source.getItemAsRecord(this.current);
			
			this.current++;
			
			// don't send null, that signals end of list
	    	if ((rec != null) && (this.consumer.handle(rec) != ReturnOption.CONTINUE))
	    		return;
		}
		
		// if we get here we must be done
		this.consumer.handle(null);
	}
}
