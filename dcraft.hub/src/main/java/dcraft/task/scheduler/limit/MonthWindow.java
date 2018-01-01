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
package dcraft.task.scheduler.limit;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;

import dcraft.xml.XElement;

public class MonthWindow {
	// window to use for this 
	protected List<MonthdayWindow> monthly = new ArrayList<MonthdayWindow>();
	
	// (0 = jan, 11 = dec)
	protected BitSet monthOfYear = new BitSet(12);
	
	public void init(LimitHelper helper, XElement config) {
		if (config != null) {
			if (config.getAttributeAsBooleanOrFalse("All"))
				this.monthOfYear.set(0, 11);
			
			if (config.getAttributeAsBooleanOrFalse("January"))
				this.monthOfYear.set(0);
			
			if (config.getAttributeAsBooleanOrFalse("February"))
				this.monthOfYear.set(1);
			
			if (config.getAttributeAsBooleanOrFalse("March"))
				this.monthOfYear.set(2);
			
			if (config.getAttributeAsBooleanOrFalse("April"))
				this.monthOfYear.set(3);
			
			if (config.getAttributeAsBooleanOrFalse("May"))
				this.monthOfYear.set(4);
			
			if (config.getAttributeAsBooleanOrFalse("June"))
				this.monthOfYear.set(5);
			
			if (config.getAttributeAsBooleanOrFalse("July"))
				this.monthOfYear.set(6);
			
			if (config.getAttributeAsBooleanOrFalse("August"))
				this.monthOfYear.set(7);
			
			if (config.getAttributeAsBooleanOrFalse("September"))
				this.monthOfYear.set(8);
			
			if (config.getAttributeAsBooleanOrFalse("October"))
				this.monthOfYear.set(9);
			
			if (config.getAttributeAsBooleanOrFalse("November"))
				this.monthOfYear.set(10);
			
			if (config.getAttributeAsBooleanOrFalse("December"))
				this.monthOfYear.set(11);
			
			// if none set, then default to all
			if (monthOfYear.cardinality() == 0)
				this.monthOfYear.set(0, 11);
			
			for (XElement el : config.selectAll("Monthday")) {
				MonthdayWindow ww = new MonthdayWindow();
				ww.init(helper, el);
				this.monthly.add(ww);
			}
			
			for (XElement el : config.selectAll("First")) {
				MonthdayWindow ww = new MonthdayWindow();
				ww.init(helper, el);
				this.monthly.add(ww);
			}
			
			for (XElement el : config.selectAll("Second")) {
				MonthdayWindow ww = new MonthdayWindow();
				ww.init(helper, el);
				this.monthly.add(ww);
			}
			
			for (XElement el : config.selectAll("Third")) {
				MonthdayWindow ww = new MonthdayWindow();
				ww.init(helper, el);
				this.monthly.add(ww);
			}
			
			for (XElement el : config.selectAll("Fourth")) {
				MonthdayWindow ww = new MonthdayWindow();
				ww.init(helper, el);
				this.monthly.add(ww);
			}
			
			for (XElement el : config.selectAll("Last")) {
				MonthdayWindow ww = new MonthdayWindow();
				ww.init(helper, el);
				this.monthly.add(ww);
			}
		}
	}
	
	/*
	 * search to see if there are any minutes open for this date after or at the current minute
	 *  
	 * @param si
	 * @return
	 */
	public LocalTime nextTimeOn(CheckInfo si) {
		// pick the first window that applies to our info
		MonthdayWindow mw = this.getApplicable(si);
		
		if (mw != null)
			return mw.nextTimeOn(si);
		
		return null;
	}

	public CheckLimitResult check(CheckInfo si) {
		// pick the first window that applies to our info
		MonthdayWindow mw = this.getApplicable(si);
		
		if (mw != null)
			return mw.check(si);
		
		return CheckLimitResult.NA;
	}

	public boolean isDateBlocked(CheckInfo ci) {
		return !this.appliesTo(ci);
	}

	public MonthdayWindow getApplicable(CheckInfo ci) {
		if (this.monthOfYear.get(ci.getMonthOfYear() - 1)) 
			for (MonthdayWindow w : this.monthly) 
				if (w.appliesTo(ci))
					return w;
		
		return null;
	}
	
	public boolean appliesTo(CheckInfo ci) {
		if (this.monthOfYear.get(ci.getMonthOfYear() - 1)) 
			return true;
		
		return false;
	}
}