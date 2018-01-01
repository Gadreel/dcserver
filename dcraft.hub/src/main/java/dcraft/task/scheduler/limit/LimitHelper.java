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
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

import dcraft.task.scheduler.ScheduleHub;
import dcraft.util.StringUtil;
import dcraft.util.TimeUtil;
import dcraft.xml.XElement;

// artificially limit expiration at 10 years, if none present
// server should be restarted at least once every 10 years :)
public class LimitHelper {
	protected DayWindow dailyWindow = new DayWindow();
	protected ZonedDateTime validFrom = null;
	protected ZonedDateTime validTo = null;
	protected ZoneId zone = null;
	
	// use only for next/check/blocked/open - do not inherit init/start/end
	protected LimitHelper parent = null; 
	
	protected List<MonthWindow> monthly = new ArrayList<MonthWindow>();
	
	protected List<WeekdayWindow> weekly = new ArrayList<WeekdayWindow>();
	
	public DayWindow getDailyWindow() {
		return this.dailyWindow;
	}
	
	/*
	 *			<Limits 
	 *				LinkBatch="None,Small,Medium,Large"   - include limits defined at server level (batch processing)
	 *				DefaultWindow="T/F"			- by default you have 24 hours enabled
					ValidFrom="iso-date-time"   - schedule only before/after these time 
					ValidTo="iso-date-time"
					TimeZone="name"				- zone to apply to the limits
	 *			>  			
	 *  	
	 *  			// one or more windows during which it is ok to run the scheduled work
	 *  			// defaults to beginning of day to end of day, no matter 
	 *  			<IncludeWindow From="00:00" To="24:00" />
	 *  
	 *  			<ExcludeWindow From="04:15" To="04:17" />
	 *  
	 *				<Weekdays Monday="T/F" Tuesday="n" ... All="T/F" >
	 *					// if exclude is not present, then assume entire day
	 *					<ExcludeWindow From="" To="" />
	 *				</Weekdays>
	 *
	 *  			<Months January="T/F" ... >
	 *  				<First Monday="T/F" Tuesday="n" ... All="T/F" >
	 *						<ExcludeWindow From="" To="" />
	 *  				</First>
	 *  				<Second Monday="T/F" Tuesday="n" ... All="T/F" >
	 *						<ExcludeWindow From="" To="" />
	 *  				</Second>
	 *  				... etc, or ...
	 *  				<Monthday List="N,N,N,Last"> 
	 *						<ExcludeWindow From="" To="" />
	 *  				</Monthday> 
	 *  			</Months>
	 *			</Limits>
	 * 
	 * @param config
	 */
	public void init(XElement config) {
		if (config != null) {
			String zone = config.getAttribute("TimeZone");
			
			if (StringUtil.isNotEmpty(zone))
				this.zone = ZoneId.of(zone);
			
			String from = config.getAttribute("ValidFrom");
			
			if (!StringUtil.isEmpty(from)) {
				this.validFrom = TimeUtil.parseDateTime(from);
				
				// TODO not sure about this - parsing of ISO string should not be circumvented?
				if (this.zone != null)
					this.validFrom = ZonedDateTime.ofInstant(this.validFrom.toInstant(), this.zone);
			}
			
			String to = config.getAttribute("ValidTo");
			
			if (!StringUtil.isEmpty(to)) {
				this.validTo = TimeUtil.parseDateTime(to);
				
				// TODO not sure about this - parsing of ISO string should not be circumvented?
				if (this.zone != null)
					this.validTo = ZonedDateTime.ofInstant(this.validTo.toInstant(), this.zone);
			}
			
			// default to 10 years from now
			if (this.validTo == null)
				this.validTo = ZonedDateTime.now().plusYears(10);
			
			for (XElement el : config.selectAll("Months")) {
				MonthWindow ww = new MonthWindow();
				ww.init(this, el);
				this.monthly.add(ww);
			}
			
			for (XElement el : config.selectAll("Weekdays")) {
				WeekdayWindow ww = new WeekdayWindow();
				ww.init(this, el);
				this.weekly.add(ww);
			}
			
			this.dailyWindow.init(config);
			
			String batch = config.getAttribute("LinkBatch");
			
			if (StringUtil.isNotEmpty(batch)) 
				this.parent = ScheduleHub.getBatch(batch);
		}
		else {
			this.dailyWindow.init(null);
		}
		
		if (this.parent != null) {
			// if parent (batch) then we only use our daily overrides, 
			// not our month or weekly  
			this.dailyWindow.setParent(this.parent.getDailyWindow());
		}
	}

	// return true if instant can run within a window
	// return false if instant is in the past
	public boolean checkForRun(ZonedDateTime v) {
		if (this.zone != null)
			v = ZonedDateTime.ofInstant(v.toInstant(), this.zone);
		
		// if this time was ended at mid or before midnight then entire day is blocked
		if (this.isEnded(v))
			return false;
		
		// if this time was not started by the end of the then entire day is blocked
		if (!this.isStarted(v))
			return false;
		
		// runs must also be now (recent) or future
		if (v.plusMinutes(5).isBefore(ZonedDateTime.now()))
			return false;
		
		if (this.parent != null) 
			return this.parent.checkForRun(v);
		
		CheckInfo ci = new CheckInfo();
		ci.setWhen(v);
		
		// if there are any months, those take precedence over other
		if (this.monthly.size() > 0) {
			for (MonthWindow ww : this.monthly)
				if (ww.appliesTo(ci)) 
					return (ww.check(ci) == CheckLimitResult.Pass);
			
			return false;
		}		
		// if there are any weeks, those take precedence over daily
		else if (this.weekly.size() > 0) {
			for (WeekdayWindow ww : this.weekly)
				if (ww.appliesTo(ci)) 
					return (ww.check(ci) == CheckLimitResult.Pass);
			
			return false;
		}
		
		return (this.dailyWindow.check(v) == CheckLimitResult.Pass);
	}

	// return the start of the next available window for running
	// from "now"
	public ZonedDateTime nextAllowedRun() {
		return this.nextAllowedRunAfter(ZonedDateTime.now().minusMinutes(1));
	}

	// return the start of the next available window for running (must always be after or equal to now)
	public ZonedDateTime nextAllowedRunAfter(ZonedDateTime lin) {
		if (this.zone != null)
			lin = ZonedDateTime.ofInstant(lin.toInstant(), this.zone);
		
		// cannot run before now - 2 minutes
		if (lin.plusMinutes(5).isBefore(ZonedDateTime.now()))
			lin = ZonedDateTime.now().minusMinutes(1);  // start back one minute so we can start on time
		
		// cannot run again
		if (this.isEnded(lin))
			return null;
		
		// must start at least at "from"
		if (!this.isStarted(lin))
			lin = this.validFrom;
		
		if (this.parent != null) 
			return this.parent.nextAllowedRunAfter(lin);
		
		CheckInfo ci = new CheckInfo();
		ci.setWhen(lin);
		
		LocalTime nt = null;
		
		// move forward 1 day at a time till we find a date that has an opening
		while (true) {
			// if there are any months, those take precedence over other
			if (this.monthly.size() > 0) {
				for (MonthWindow ww : this.monthly)
					if (ww.appliesTo(ci)) {
						nt = ww.nextTimeOn(ci);
						break;
					}
			}
			// if there are any weeks, those take precedence over daily
			else if (this.weekly.size() > 0) {
				for (WeekdayWindow ww : this.weekly) 
					if (ww.appliesTo(ci)) {
						nt = ww.nextTimeOn(ci);
						break;
					}
			}
			else
				nt = this.dailyWindow.nextTimeOn(ci.getWhen());
			
			if (nt != null)
				break;
			
			ci.incrementDay();
			
			// there is no next allowed
			if (this.isEnded(ci.getWhen()))
				return null;
		}
		
		lin = TimeUtil.withTime(ci.getWhen(), nt);
		
		// there is no next allowed
		if (this.isEnded(lin))
			return null;
		
		return lin;
	}

	public boolean isDateBlocked(ZonedDateTime tlast) {
		if (this.zone != null)
			tlast = ZonedDateTime.ofInstant(tlast.toInstant(), this.zone);
		
		// if this time was ended at mid or before midnight then entire day is blocked
		if (this.isEnded(tlast.withHour(0).withMinute(0).withSecond(0).withNano(0)))
			return true;
		
		// if this time was not started by the end of the then entire day is blocked
		if (!this.isStarted(tlast.withHour(23).withMinute(59).withSecond(59).withNano(0)))
			return true;
		
		if (this.parent != null) 
			return this.parent.isDateBlocked(tlast);
		
		CheckInfo ci = new CheckInfo();
		ci.setWhen(tlast);

		// if there are any months, those take precedence over other
		if (this.monthly.size() > 0) {
			for (MonthWindow ww : this.monthly)
				if (ww.appliesTo(ci))
					return false;
			
			return true;
		}
		// if there are any weeks, those take precedence over daily		
		else if (this.weekly.size() > 0) {
			// only need to find one window to return false
			for (WeekdayWindow ww : this.weekly)
				if (ww.appliesTo(ci))
					return false;
			
			return true;
		}
		
		return this.dailyWindow.excludeAll();
	}

	// return true if "now" is after valid start date 
	public boolean isStarted() {
		if (this.validFrom != null)
			return !this.validFrom.isAfter(ZonedDateTime.now());		// now is equal or greater than from
		
		return true;
	}
	
	// return true if param is after valid start date 
	public boolean isStarted(ZonedDateTime scheduleDate) {
		if (this.validFrom != null)
			return !this.validFrom.isAfter(scheduleDate);		// now is equal or greater than from
		
		return true;
	}

	// return true if "now" is after valid end date 
	public boolean isEnded() {
		if (this.validTo != null)
			return !this.validTo.isAfter(ZonedDateTime.now());			// must be before now (not equal)
		
		return false;
	}

	// return true if param is after valid end date
	public boolean isEnded(ZonedDateTime scheduleDate) {
		if (this.validTo != null)
			return !this.validTo.isAfter(scheduleDate);
		
		return false;
	}

	// return the first valid date for this schedule
	public ZonedDateTime getFirstDate() {
		return this.validFrom;
	}

	// return the last valid date for this schedule
	public ZonedDateTime getLastDate() {
		return this.validTo;
	}
}
