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
package dcraft.task.scheduler.common;

import java.time.ZonedDateTime;

import dcraft.filestore.CommonPath;
import dcraft.hub.ResourceHub;
import dcraft.hub.app.ApplicationHub;
import dcraft.log.Logger;
import dcraft.script.Script;
import dcraft.struct.RecordStruct;
import dcraft.task.ContextShimWork;
import dcraft.task.IWork;
import dcraft.task.scheduler.BaseSchedule;
import dcraft.task.scheduler.SimpleSchedule;
import dcraft.task.scheduler.limit.LimitHelper;
import dcraft.util.StringUtil;
import dcraft.util.TimeUtil;
import dcraft.xml.XElement;

public class CommonSchedule extends BaseSchedule {
	static public final int METHOD_NONE = 0;
	static public final int METHOD_STANDARD = 1;
	static public final int METHOD_SCRIPT = 2;
	static public final int METHOD_CLASS = 3;
	
	static public CommonSchedule of(RecordStruct task) {
		CommonSchedule sch = new CommonSchedule();

		sch.repeat = true;
		sch.setTask(task);

		return sch;
	}
	
	// what method is used to calculate the run times
	protected int method = CommonSchedule.METHOD_NONE;

	protected IScheduleHelper helper = null;
	
	// limits handler
	protected LimitHelper limits = new LimitHelper();
	
	// when was this last run, leave null if not important
	protected ZonedDateTime last = null;
	
	protected IInlineScript iscript = null;
	
	protected String workClass = null;
	
	protected CommonPath workScript = null;
	
	public void setLastRun(ZonedDateTime v) {
		this.last = v;
	}
	
	public ZonedDateTime getLastRun() {
		return this.last;
	}
	
	public void setInlineScript(IInlineScript v) {
		this.iscript = v;
	}
	
	public IInlineScript getInlineScript() {
		return this.iscript;
	}

	@Override
	public IWork getWork() {
		// use context shim so that the correct script and class loaders are available at the time
		// the work is running - in the right context

		if (this.workScript != null)
			return ContextShimWork.ofScript(this.workScript);
		
		if (StringUtil.isNotEmpty(this.workClass))
			return ContextShimWork.ofClass(this.workClass);
		
		return null;
	}
	
	protected CommonSchedule() { }
	
	/*
	 *  	<CommonSchedule 
	 *  		Method="None,Standard,Script,Class"
	 *  		View="Period,Daily,Weekly,Monthly,Script,Custom"    - for the UI to determine which pane to show 
	 *  		ClassName="n"		- use the bundle provided, if any, to load the class
	 *  							- must implement IScheduleHelper
	 *  	>
	 *			<Limits ... />		- see LimitHelper
	 *  
	 *  		// use ISO periods, e.g. PT2H30M10S
	 *  		// used for intra-daily mostly, but can be any
	 *  		<Period Value="n" />   
	 *  
	 *  		// if method = Daily, these are the times to run, ignore frequency
	 *  		<Daily>
	 *  			<Schedule At="" RunIfMissed="True/False" />
	 *  			<Schedule At="" RunIfMissed="True/False" />
	 *  			<Schedule At="" RunIfMissed="True/False" />
	 *  		</Daily>
	 *  
	 *  		// if method = Weekly, these are the days to run.  may be more than one WeekDays, use first match
	 *  		<Weekly>
	 *  			<Weekdays Monday="T/F" Tuesday="n" ... All="T/F" >
	 *  				<Schedule At="" RunIfMissed="True/False" />
	 *  			</Weekdays>
	 *  		</Weekly>
	 *  
	 *  		// if method = "Monthly" (may be more than Months, etc)
	 *  		// excludes for monthly don't make sense, but are there 
	 *  		<Monthly>
	 *  			<Months January="T/F" ... All="T/F" >
	 *  				<First Monday="T/F" Tuesday="n" ... All="T/F" >
	 *  					<Schedule At="" RunIfMissed="True/False" />
	 *  				</First>
	 *  				<Second Monday="T/F" Tuesday="n" ... All="T/F" >
	 *  					<Schedule At="" RunIfMissed="True/False" />
	 *  				</Second>
	 *  				... etc, or ...
	 *  				<Monthday List="N,N,N,Last">
	 *  					<Schedule At="" RunIfMissed="True/False" />
	 *  				</Monthday> 
	 *  			</Months>
	 *  		</Monthly>
	 *  
	 *  		// _last is available, but = null if first run
	 *  		// _now is available 
	 *  		// if method != Script then there is an obj _suggested
	 *  		// to call "suggest.next()" that will provide next based
	 *  		// on the method settings (so scriptold is more a filter)
	 *  		// when method is scriptold then no hints are provided
	 *  		<Script> run when scheduling next </Script>
	 *  	</CommonSchedule>
	 */
	
	public void init(XElement config) {
		super.init(config);
		
		// TODO load config, if classes are involved then use custom loader if available
		
		if (config != null) {
			this.limits.init(config.find("Limits"));
			
			// what method is used to calculate the run times
			String meth = config.getAttribute("Method", "Standard");
			
			XElement helpel = null;
			
			if ("Standard".equals(meth)) {
				this.method = CommonSchedule.METHOD_STANDARD;
				
				helpel = config.find("Period");
				
				if (helpel != null) {
					this.helper = new PeriodHelper();
				}
				else {					
					helpel = config.find("Daily");
					
					if (helpel != null) {
						this.helper = new DailyHelper();
					}
					else {
						helpel = config.find("Weekly");
						
						if (helpel != null) {
							this.helper = new WeekdayHelper();
						}
						else {
							helpel = config.find("Monthly");
							
							if (helpel != null) {
								this.helper = new MonthHelper();
							}
							else {
								// TODO log
								System.out.println("schedule does not appear to have a helper");
							}
						}
					}					
				}
			}
			else if ("Script".equals(meth)) {
				this.method = CommonSchedule.METHOD_SCRIPT;
				
				XElement sel = config.find("ScheduleScript");
				
				if (sel != null) {
					/*  TODO
					String code = sel.getText();
					
					if (!StringUtil.isBlank(code)) {
						this.scriptold = new Script();
						
						try {
							this.scriptold.setScript(code);
						} 
						catch (Exception e) {
							// TODO log
						}
					}
					
					// TODO
					 * 
					 */
				}
			}
			else if ("Class".equals(meth)) {
				this.method = CommonSchedule.METHOD_CLASS;
				
				String className = config.getAttribute("ScheduleClassName");
				
				try {
					// TODO
					//this.helper = (IScheduleHelper) ((this.customLoader != null) 
					//	? this.customLoader.getInstance(className) 
					//	: Class.forName(className).newInstance());
					
					this.helper = (IScheduleHelper) ResourceHub.getResources().getClassLoader().getInstance(className);
					
					helpel = config;
				} 
				catch (Exception e) {
					// TODO log
					System.out.println("unable to load schedule helper class: " + className);
				}	
			}
			
			if (this.helper != null) {
				this.helper.setLimits(this.limits);
				this.helper.setLast(this.last);
				this.helper.init(this, helpel);
			}
			
			String script = config.getAttribute("Script");

			if (StringUtil.isNotEmpty(script)) {
				this.workScript = CommonPath.from(script);
			}
			else {
				String className = config.getAttribute("ClassName");

				if (StringUtil.isNotEmpty(className)) {
					this.workClass = className;
				}
			}
		}

		// setup the first run
		this.reschedule();
	}
	
	public void init(IScheduleHelper helper, XElement limits, XElement hconfig) {
		// TODO load config, if classes are involved then use custom loader if available
		
		this.limits.init(limits);
		
		this.method = CommonSchedule.METHOD_CLASS;
		
		this.helper = helper;
		
		this.helper.setLimits(this.limits);
		this.helper.setLast(this.last);
		this.helper.init(this, hconfig);

		// setup the first run
		this.reschedule();
	}

	// same as reschedule, except we must move forward at least one day
	public boolean rescheduleOnNextDate() {
		this.last = TimeUtil.nextDayAtMidnight(this.last);		
		return this.reschedule();
	}

	@Override
	public boolean reschedule() {
		// it is important to remove the old observers because we are going to add a new one - us - and the others 
		//if (this.task != null)
		//	this.task.clearObservers();
		
		Logger.info("Rescheduling BATCH job: " + this.scheduleid);
		
		
		if (this.helper != null) {
			// TODO if scriptold then use that to help with finding next
			this.last = this.helper.next();
			
			Logger.info("Rescheduled BATCH: " + this.getTitle() + " next run " + this.last);
			
			//System.out.println("rescheduled: " + ((this.last == null) ? "null" : this.last.toString()));
			
			return (this.last != null);
		}
		/* TODO
		else if (this.scriptold != null) {
			this.scriptold.addVariable("_now", new DateTime());
			this.scriptold.addVariable("_last", this.last);
			this.scriptold.addVariable("_sched", this);
			this.scriptold.addVariable("_data", this.userData);
			
			try {
				Object o = this.scriptold.call("schedule");	// TODO consider passing params rather than set vars above
				
				if (o instanceof DateTime)
					this.last = (DateTime)o;
				else
					System.out.println("bad return value: " + o);		// TODO log
			}
			catch (Exception x) {
				System.out.println("bad return value: " + x);		// TODO log
			}
			
			System.out.println("rescheduled scriptold: " + ((this.last == null) ? "null" : this.last.toString()));
			
			return (this.last != null);
		}
		*/
		else if (this.iscript != null) {
			try {
				this.last = this.iscript.schedule(this);	// TODO consider passing params rather than set vars above	
			}
			catch (Exception x) {
				System.out.println("bad return value: " + x);		// TODO log
			}
			
			System.out.println("rescheduled scriptold: " + ((this.last == null) ? "null" : this.last.toString()));
			
			return (this.last != null);
		}
		
		return false;
	}

	@Override
	public long when() {
		if (this.last != null)
			return this.last.toInstant().toEpochMilli();
		
		return -1;
	}
}
