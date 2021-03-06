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

import java.math.BigDecimal;
import java.math.RoundingMode;

import dcraft.struct.RecordStruct;

public class NumberCounterAdv extends Counter {
	protected BigDecimal value = null; 
	protected BigDecimal low = null;
	protected BigDecimal high = null;
	protected BigDecimal sum = null;
	protected long setcount = 0;
	
	public void setValue(long value) {
		this.setValue(BigDecimal.valueOf(value));
	}

	public void setValue(double value) {
		this.setValue(BigDecimal.valueOf(value));
	}
	
	public void setValue(BigDecimal value) {
		try {
			this.valuelock.lockInterruptibly();
		} 
		catch (InterruptedException x) {
			return;   // should only happen under bad conditions, so probably nothing we can do
		}
		
		// we are locked during whole set/notify process so be efficient 
		try {
			this.value = value;
			
			if (this.value != null) {
				if ((this.high == null) || (this.value.compareTo(this.high) > 0))
					this.high = this.value;
				
				if ((this.low == null) || (this.value.compareTo(this.low) < 0))
					this.low = this.value;
				
				this.setcount++;
				
				if (this.sum == null)
					this.sum = this.value;
				else
					this.sum = this.sum.add(this.value);
			}
			
	    	this.setChanged();
		}
		finally {
			this.valuelock.unlock();
		}
	}
	
	public BigDecimal getValue() {
		return this.value;
	}
	
	public BigDecimal getHigh() {
		return this.high;
	}
	
	public BigDecimal getLow() {
		return this.low;
	}
	
	public BigDecimal getSum() {
		return this.sum;
	}
	
	public long getSetCount() {
		return this.setcount;
	}
	
	public BigDecimal getAverage() {
		if ((this.sum == null) || (this.sum.longValue() == 0))
			return null;
		
		return this.sum.divide(new BigDecimal(this.setcount), RoundingMode.HALF_UP);
	}
	
	public NumberCounterAdv(String name) {
		super(name);
	}

	public NumberCounterAdv(String name, BigDecimal value) {
		this(name);
		
		this.setValue(value);
	}

	public void increment() {
		try {
			this.valuelock.lockInterruptibly();
		} 
		catch (InterruptedException x) {
			return;   // should only happen under bad conditions, so probably nothing we can do
		}
		
		// we are locked during whole set/notify process so be efficient 
		try {
			if (this.value == null)
				this.setValue(1);
			else
				this.setValue(this.value.add(BigDecimal.valueOf(1)));
		}
		finally {
			this.valuelock.unlock();
		}
	}
	
	@Override
	public Counter clone() {
		NumberCounterAdv clone = new NumberCounterAdv(this.name, this.value);
		this.copyToClone(clone);
		return clone;
	}
	
	@Override
	public void copyToClone(Counter clone) {
		super.copyToClone(clone);
		
		// caller is doing weird stuff - ignore them
		if (!(clone instanceof NumberCounterAdv))
			return;
		
		NumberCounterAdv nclone = (NumberCounterAdv)clone; 
		
		nclone.low = this.low;
		nclone.high = this.high;
		nclone.sum = this.sum;
		nclone.setcount = this.setcount;
	}
	
	@Override
	public RecordStruct toRecord() {
		return RecordStruct.record()
				.with("Name", this.name)
				.with("Value", this.value)
				.with("High", this.high) 
				.with("Low", this.low)
				.with("Sum", this.sum) 
				.with("SetCount", this.setcount)
				.with("Average", this.getAverage()) 
				.with("Object", this.currentObject);
	}
	
	@Override
	public RecordStruct toCleanRecord() {
		return RecordStruct.record()
				.with("Name", this.name)
				.with("Value", this.value)
				.with("High", this.high) 
				.with("Low", this.low)
				.with("Sum", this.sum) 
				.with("SetCount", this.setcount)
				.with("Average", this.getAverage());
	}
	
	@Override
	public void reset() {
		super.reset();
		
		this.value = null; 
		this.low = null;
		this.high = null;
		this.sum = null;
		this.setcount = 0;
	}
}
