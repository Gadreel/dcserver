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
import dcraft.stream.IStreamSource;
import dcraft.stream.ReturnOption;
import dcraft.struct.RecordStruct;
import dcraft.task.IParentAwareWork;
import dcraft.xml.XElement;

abstract public class SyncRecordStreamSupplier extends BaseRecordStream implements IStreamSource, IRecordStreamSupplier {
	protected SyncRecordStreamSupplier() {
	};
	
	@Override
	public void init(IParentAwareWork stack, XElement el) {
	}
	
	abstract public RecordStruct get();

	/**
	 * Someone downstream wants more data
	 */
	@Override
	public void read() throws OperatingContextException {
		while (true) {
			RecordStruct rec = this.get();
			
	    	if (this.consumer.handle(rec) != ReturnOption.CONTINUE)
	    		return;
	    	
	    	if (rec == null)
	    		break;
		}
	}
}
