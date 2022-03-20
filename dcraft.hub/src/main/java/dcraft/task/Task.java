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
package dcraft.task;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import dcraft.filestore.CommonPath;
import dcraft.filestore.local.LocalStore;
import dcraft.hub.ResourceHub;
import dcraft.hub.app.ApplicationHub;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
import dcraft.hub.op.OperationController;
import dcraft.hub.op.UserContext;
import dcraft.log.Logger;
import dcraft.script.Script;
import dcraft.service.plugin.Operation;
import dcraft.struct.BaseStruct;
import dcraft.struct.ListStruct;
import dcraft.struct.RecordStruct;
import dcraft.struct.Struct;
import dcraft.task.run.WorkAdapter;
import dcraft.task.run.WorkTopic;
import dcraft.util.RndUtil;
import dcraft.util.StringUtil;

// conforms to dcTaskInfo data type
public class Task extends RecordStruct {
	static public String nextTaskId() {
		  return Task.nextTaskId("DEFAULT");
	}	
	
	static public String nextTaskId(String part) {
		  return RndUtil.nextUUId()  + "-" + part + "-" +  ApplicationHub.getNodeId();
	}	
	
	public static Task ofSubtask(String subtitle, String suffix) throws OperatingContextException {
		OperationContext ctx = OperationContext.getOrThrow();
		
		if (ctx instanceof TaskContext)
			return Task.ofSubtask((TaskContext) ctx, subtitle, suffix);
		
		return Task.of(ctx)
				.withId(Task.nextTaskId(suffix))
				.withTitle("Subtask: " + subtitle);
	}	
	
	public static Task ofSubtask(TaskContext ctx, String subtitle, String suffix) {
		return Task.of(ctx)
			.withId(ctx.getTask().getId() + "_" + suffix)
			.withTitle(ctx.getTask().getTitle() + " - Subtask: " + subtitle)
			.withTimeout(ctx.getTask().getTimeout())
			.withThrottle(ctx.getTask().getThrottle());
	}
	
	/* prefer subtask, when appropriate
	 */ 
	static public Task of(OperationContext ctx) {
		return new Task()
				.withContext(ctx);
				//.withController(ctx.getController())
				//.withUser(ctx.getUserContext());
	}
	
	static public Task ofSubContext() throws OperatingContextException {
		return Task.of(OperationContext.getOrThrow());
	}

	static public Task ofHubRoot() {
		return Task.of(OperationContext.context(UserContext.rootUser()));
	}

	static public Task ofHubRootSameSite() throws OperatingContextException {
		UserContext userContext = OperationContext.getOrThrow().getUserContext();

		OperationContext ctx = OperationContext.context(
				UserContext.rootUser(userContext.getTenantAlias(), userContext.getSiteAlias())
		);

		return Task.of(ctx);
	}

	static public Task ofHubRoot(String tenant, String site) {
		return Task.of(OperationContext.context(UserContext.rootUser(tenant, site)));
	}

	static public Task ofHubGuest() {
		return Task.of(OperationContext.context(UserContext.guestUser()));
	}
	
	static public Task ofContext(RecordStruct ctx) {
		return new Task()
				.withContext(ctx);
	}
	
	static public Task of(RecordStruct info) {
		Task task = new Task();
		task.copyFields(info);
		return task;
	}
	
	static public Task ofWork(RecordStruct info) {
		RecordStruct usr = info.getFieldAsRecord("User");
		
		if (! "Root".equals(usr.getFieldAsString("Mode"))) {
			Logger.error("Work user mode not supported.");
			return null;
		}
		
		UserContext usrctx = UserContext.rootUser(usr.getFieldAsString("Tenant"), usr.getFieldAsString("Site"));
		
		info.removeField("User");

		Task task = new Task();
		task.copyFields(info);
		
		task.withContext(OperationContext.context(usrctx));
		
		return task;
	}
	
	// MEMBERS
	
	// used during run
	protected IWork work = null;
	//protected OperationController controller = null;

	// used with run or queueable
	//protected UserContext user = null;
	//protected RecordStruct info = new RecordStruct();

	protected Task() {
		this
				.with("Context", RecordStruct.record())
				.with("OriginNodeId", ApplicationHub.getNodeId())
				.with("RunStamp", ZonedDateTime.now(ZoneId.of("UTC")));
	}

	/*
	protected Task(RecordStruct info) {
		this.copyFields(info);
	}
	*/

	public RecordStruct freezeToRecord() {
		RecordStruct clone = this.deepCopy();
		
		//if (this.user != null)
		//	clone.getFieldAsRecord("Context").with("User", this.user.freezeToRecord());
		
		// these never get propagated
		clone.removeField("ClaimId");
		clone.removeField("ClaimPath");
		clone.removeField("AuditId");
		
		return clone;
	}

	@Override
	public Task deepCopy() {
		Task cp = new Task();
		this.doCopy(cp);
		return cp;
	}

	/*
	public Task withUser(UserContext v) {
		this.user = v;
		this.getFieldAsRecord("Context").with("User", this.user.freezeToRecord());
		return this;
	}

	public UserContext getUser() {
		if (this.user == null)
			this.user = UserContext.user(this.getFieldAsRecord("Context").getFieldAsRecord("User"));
		
		return this.user;
	}

	public Task withController(OperationController v) {
		this.getFieldAsRecord("Context").with("Controller", v);
		return this;
	}

	protected OperationController getController() {
		return (OperationController) this.getFieldAsRecord("Context").getFieldAsRecord("Controller");
	}
	*/

	public Task withWork(IWork work) {
		this.work = work;
		
		if (this.work != null)
			this.with("WorkClassname", this.work.getClass().getCanonicalName());
		
		return this;
	}
	
	public Task withWork(Runnable work) {
		return this.withWork(new WorkAdapter(work));
	}
	
	public Task withWork(Class<? extends IWork> classref) {
		this.with("WorkClassname", classref.getCanonicalName());
		return this;
	}
	
	// class name
	public Task withWork(String classname) {
		this.with("WorkClassname", classname);
		return this;
	}
	
	// script path
	public Task withScript(String path) {
		if (! path.endsWith(".dcs.xml"))
			path += ".dcs.xml";

		this.with("ScriptPath", path);

		return this;
	}
	
	public Task withNodeScript(String path) {
		this.with("ScriptPath", path);

		return this;
	}

	public Task withScript(CommonPath path) {
		this.with("ScriptPath", path);
		return this;
	}

	public Task withScript(Path path) {
		this.with("ScriptLocalPath", path);
		return this;
	}
	
	public IWork buildWork() {
		if (this.work == null) {
			if (this.isNotFieldEmpty("WorkClassname")) {
				this.work = (IWork) ResourceHub.getResources().getClassLoader().getInstance(this.getWorkClassname());
			}
			else if (this.isNotFieldEmpty("ScriptPath")) {
				String scriptPath = this.getFieldAsString("ScriptPath");

				if (scriptPath.endsWith(".xml")) {
					Script scrpt = Script.of(CommonPath.from(scriptPath));

					if (scrpt != null) {
						if (this.isFieldEmpty("Title"))
							this.with("Title", scrpt.getTitle());

						this.work = scrpt.toWork();
					}
				}
				else if (scriptPath.endsWith(".js")) {
					this.work = NodeWork.of(CommonPath.from(scriptPath));
				}
			}
			else if (this.isNotFieldEmpty("ScriptLocalPath")) {
				Script scrpt = Script.of(Paths.get(this.getFieldAsString("ScriptLocalPath")));
				
				if (scrpt != null) {
					if (this.isFieldEmpty("Title"))
						this.with("Title", scrpt.getTitle());
					
					this.work = scrpt.toWork();
				}
			}
		}
		
		return this.work;
	}
	
	public IWork getWorkIfPresent() {
		return this.work;
	}
	
	public String getWorkClassname() {
		return this.getFieldAsString("WorkClassname");
	}
	
	protected void cleanUp() {
		this.work = null;
		//this.controller = null;
		//this.user = null;
	}
	
	public String getWorkName() {
		if (this.work != null)
			return this.work.getClass().getName();
		
		return this.getFieldAsString("WorkClassname");
	}
	
	public Task withTopic(String v) {
		this.with("Topic", v);
		return this;
	}

	public String getTopic() {
		String name = this.getFieldAsString("Topic");
		
		if (StringUtil.isEmpty(name))
			name = WorkTopic.DEFAULT;
		
		return name;
	}
	
	public Task withContext(RecordStruct v) {
		/* we need to call this before schema are loaded so cannot check, but the principle is helpful
		if (! SchemaHub.validateType(v, "OperationContext")) {
			Logger.error("Unable to set user, invalid record");
			return this;
		}
		*/
		
		this.with("Context", v);
		//this.user = null;
		return this;
	}

	public RecordStruct getContext() {
		return this.getFieldAsRecord("Context");
	}
		
	public Task withObserver(String classname) {
		if (StringUtil.isEmpty(classname))
			return this;
		
		return this.withObserverRec(new RecordStruct().with("Classname", classname));
	}
	
	public Task withObserverRec(RecordStruct observer) {
		ListStruct buildobservers = this.getFieldAsList("Observers");
		
		if (buildobservers == null) {
			buildobservers = new ListStruct();
			this.with("Observers", buildobservers);
		}
		
		buildobservers.withItem(observer);
		
		return this;
	}
	
	public Task withDefaultLogger() {
		return this.withObserver("dcraft.task.TaskLogger");
	}
	
	public ListStruct getObservers() {
		return this.getFieldAsList("Observers");
	}
	
	public Task withId(String v) {
		this.with("Id", v);
		return this;
	}
	
	public Task withNextId(String name) {
		this.with("Id", Task.nextTaskId(name));
		return this;
	}
	
	public Task withNextId() {
		this.with("Id", Task.nextTaskId());
		return this;
	}
	
	public String getId() {
		return this.getFieldAsString("Id");
	}
	
	public Task withTitle(String v) {
		this.with("Title", v);
		return this;
	}
	
	public String getTitle() {
		return this.getFieldAsString("Title");
	}
	
	public Task withTargetNodeId(String v) {
		this.with("TargetNodeId", v);
		return this;
	}
	
	public String getTargetNodeId() {
		return this.getFieldAsString("TargetNodeId");
	}
	
	public Task withOriginNodeId(String v) {
		this.with("OriginNodeId", v);
		return this;
	}
	
	public String getOriginNodeId() {
		return this.getFieldAsString("OriginNodeId");
	}
	
	public Task withMaxTries(int v) {
		this.with("MaxTries", v);
		return this;
	}
	
	public int getMaxTries() {
		return (int)this.getFieldAsInteger("MaxTries", 1);
	}
	
	public Task withThrottle(int v) {
		this.with("Throttle", v);
		return this;
	}
	
	public Task withThrottleIfEmpty(int v) {
		if (this.isFieldEmpty("Throttle"))
			this.with("Throttle", v);
		
		return this;
	}
	
	// default to 2 resumes
	public int getThrottle() {
		return (int)this.getFieldAsInteger("Throttle", 2);
	}
	
	public Task withRunStamp(ZonedDateTime v) {
		this.with("RunStamp", v);
		return this;
	}
	
	public ZonedDateTime getRunStamp() {
		return this.getFieldAsDateTime("RunStamp");
	}
	
	
	public Task withSetTags(String... v) {
		this.with("Tags", ListStruct.list((Object[])v));
		return this;
	}
	
	public Task withSetTags(ListStruct v) {
		this.with("Tags", v);
		return this;
	}
	
	public Task withAddTags(String... v) {
		if (this.isFieldEmpty("Tags"))
			this.with("Tags", ListStruct.list((Object[])v));
		else
			this.getFieldAsList("Tags").withItem((Object[])v);
		
		return this;
	}
	
	public Task withAddTags(ListStruct v) {
		if (this.isFieldEmpty("Tags"))
			this.with("Tags", v);
		else
			this.getFieldAsList("Tags").withItem(v);
		
		return this;
	}
	
	public ListStruct getTags() {
		return this.getFieldAsList("Tags");
	}
	
	/**
	 * @param tags to search for with this task
	 * @return true if this task has one of the requested tags  
	 */
	public boolean isTagged(String... tags) {
		if (this.isFieldEmpty("Tags"))
			return false;
		
		for (BaseStruct shas : this.getFieldAsList("Tags").items()) {
			String has = shas.toString();
			
			for (String wants : tags) {
				if (has.equals(wants))
					return true;
			}
		}
		
		return false;
	}
	
	public Task withParams(BaseStruct v) {
		this.with("Params", v);
		return this;
	}
	
	public BaseStruct getParams() {
		return this.getField("Params");
	}
	
	public RecordStruct getParamsAsRecord() {
		return this.getFieldAsRecord("Params");
	}
	
	public Task withHints(RecordStruct v) {
		this.with("Hints", v);
		return this;
	}
	
	public RecordStruct getHints() {
		return this.getFieldAsRecord("Hints");
	}
	
	synchronized public RecordStruct getCreateHints() {
		RecordStruct v = this.getFieldAsRecord("Hints");
		
		if (v == null) {
			v = RecordStruct.record();
			this.with("Hints", v);
		}
		
		return v;
	}
	
	/*
	 * Timeout is when nothing happens for v minutes...see Overdue also
	 * 
	 * @param v
	 * @return
	 */
	public Task withTimeout(int v) {
		this.with("Timeout", v);
		
		//if (v > this.getDeadline())
		//	this.withDeadline(v + 1);
		
		return this;
	}
	
	// in minutes
	public int getTimeout() {
		return (int) this.getFieldAsInteger("Timeout", 1);
	}
	
	public int getTimeoutMS() {
		return (int) this.getFieldAsInteger("Timeout", 1)  * 60 * 1000; // convert to ms
	}

	public String getRetryPlan() {
		return this.getFieldAsString("RetryPlan");
	}

	public Task withRetryPlan(String v) {
		this.with("RetryPlan", v);
		return this;
	}

	/*
	 * Deadline is v minutes until the task must complete, see Timeout also
	 * 
	 * @param v
	 * @return
	 */
	public Task withDeadline(int v) {
		this.with("Deadline", v);
		return this;
	}
	
	// stalled even if still active, not getting anything done
	// in minutes
	public int getDeadline() {
		return (int) this.getFieldAsInteger("Deadline", 0);
	}
	
	public int getDeadlineMS() {
		return (int) this.getFieldAsInteger("Deadline", 0)  * 60 * 1000; // convert to ms
	}
	
	public boolean validate() {
		return this.validate("Task");
	}

	// happens after submit to pool or to queue
	public void prep() {
		if (this.isFieldEmpty("Title"))
			this.with("Title", "[unnamed]");
		
		if (this.isFieldEmpty("Id"))
			this.with("Id", Task.nextTaskId());
	}

	@Override
	public String toString() {
		return this.getTitle() + " (" + this.getId() + ")";
	}

	// --------- for work queue only ---------
	
	public String getClaimId() {
		return this.getFieldAsString("ClaimId");
	}
	
	public Task withClaimId(String v) {
		this.with("ClaimId", v);
		return this;
	}
	
	public String getClaimPath() {
		return this.getFieldAsString("ClaimPath");
	}
	
	public Task withClaimPath(String v) {
		this.with("ClaimPath", v);
		return this;
	}
	
	public String getRunId() {
		return this.getFieldAsString("RunId");
	}
	
	public Task withRunId(String v) {
		this.with("RunId", v);
		return this;
	}
	
	public Path getWorkPath() {
		return ApplicationHub.getDeploymentNodePath()
				.resolve("tasks")
				.resolve(this.getId());
	}
	
	public Path getRunPath() {
		Path auditpath = ApplicationHub.getDeploymentNodePath()
				.resolve("tasks")
				.resolve(this.getId())
				.resolve(this.getRunId());
		
		try {
			Files.createDirectories(auditpath);
			
			return auditpath;
		}
		catch (Exception x) {
			Logger.error("Unable to create task run folder");
		}
		
		return null;
	}
	
	/* TODO move
	public RecordStruct status() {
		return new RecordStruct() 
			.with("TaskId", this.getField("Id"))
			.with("MaxTry", this.getMaxTries())
			.with("Added", this.getRunStamp())
			.with("Try", this.getCurrentTry());
	}
	*/
}
