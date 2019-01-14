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

import java.util.UUID;

import dcraft.struct.RecordStruct;
import dcraft.task.ISchedule;
import dcraft.task.IWork;
import dcraft.task.Task;
import dcraft.util.RndUtil;
import dcraft.xml.XElement;

abstract public class BaseSchedule implements ISchedule {
	protected RecordStruct task = null;
	protected boolean repeat = false;
	protected String scheduleid = null;
	protected RecordStruct hints = new RecordStruct();
	protected boolean canceled = false;
	protected XElement config = null;

	abstract public IWork getWork();

	public void setTask(RecordStruct v) {
		this.task = v;
	}
	
	@Override
	public String getCreateId() {
		if (this.scheduleid == null)
			this.scheduleid = "TEMP_" + RndUtil.nextUUId();
		
		return this.scheduleid;
	}
	
	@Override
	public void setId(String v) {
		this.scheduleid = v;
	}
	
	public BaseSchedule() {
	}
	
	@Override
	public String getTitle() {
		return this.task.getFieldAsString("Title");
	}
	
	@Override
	public Task getTask() {
		return Task.of(this.task).withWork(this.getWork());
	}
	
	@Override
	public void init(XElement config) {
		this.config = config;
	}
	
	@Override
	public void cancel() {
		this.canceled = true;
	}
	
	@Override
	public boolean isCancelled() {
		return this.canceled;
	}
	
	@Override
	public RecordStruct getHints() {
		return this.hints;
	}
	
	@Override
	public void setHint(String name, Object value) {
		this.hints.with(name, value);
	}
}
