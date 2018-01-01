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
package dcraft.log.count;

import dcraft.struct.RecordStruct;

public class BooleanCounter extends Counter {
	protected Boolean value = null; 

	public void setValue(Boolean value) {
		try {
			this.valuelock.lockInterruptibly();
		} 
		catch (InterruptedException x) {
			return;   // should only happen under bad conditions, so probably nothing we can do
		}
		
		// we are locked during whole set/notify process so be efficient 
		try {
			this.value = value;
			
	    	this.setChanged();
		}
		finally {
			this.valuelock.unlock();
		}
	}
	
	public Boolean getValue() {
		return this.value;
	}
	
	public BooleanCounter(String name) {
		super(name);
	}

	public BooleanCounter(String name, Boolean value) {
		this(name);
		
		this.setValue(value);
	}
	
	@Override
	public Counter clone() {
		BooleanCounter clone = new BooleanCounter(this.name, this.value);
		this.copyToClone(clone);
		return clone;
	}
	
	@Override
	public RecordStruct toRecord() {
		return RecordStruct.record()
				.with("Name", this.name) 
				.with("Value", this.value)
				.with("Object", this.currentObject);
	}
	
	@Override
	public RecordStruct toCleanRecord() {
		return RecordStruct.record()
				.with("Name", this.name) 
				.with("Value", this.value);
	}
	
	@Override
	public void reset() {
		super.reset();
		
		this.value = null;
	}
}
