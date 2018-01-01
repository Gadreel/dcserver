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
package dcraft.task.scheduler;

import dcraft.struct.RecordStruct;
import dcraft.task.IWork;
import dcraft.task.Task;
import dcraft.util.StringUtil;
import dcraft.xml.XElement;

public class SimpleSchedule extends BaseSchedule {
	static public SimpleSchedule of(RecordStruct task, IWork work, long at, int repeatevery) {
		SimpleSchedule sch = new SimpleSchedule();
		
		sch.nextrunat = at;
		
		if (repeatevery > 0) {
			sch.repeat = true;
			sch.every = repeatevery;
		}
		
		sch.setTask(task);
		sch.setWork(work);
		
		return sch;
	}
	
	protected long nextrunat = 0;
	protected int every = 0;
	
	protected SimpleSchedule() {
	}
	
	public void init(XElement config) {
		super.init(config);
		
		if ((config != null) && config.hasAttribute("Seconds")) {
			long every = StringUtil.parseInt(config.getAttribute("Seconds"), 0);
			
			if (every > 0) {
				this.repeat = true;
				this.every = (int)every;
				
				this.nextrunat = System.currentTimeMillis() + (this.every * 1000);
			}
		}
	}
	
	@Override
	public boolean reschedule() {
		if (! this.repeat || this.canceled)
			return false;
		
		this.nextrunat += (this.every * 1000);
		return true;
	}

	@Override
	public long when() {
		return this.nextrunat;
	}
}
