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
package dcraft.task.queue;

import dcraft.db.DatabaseAudit;
import dcraft.filestore.CommonPath;
import dcraft.hub.app.ApplicationHub;
import dcraft.hub.op.OperatingContextException;
import dcraft.log.Logger;
import dcraft.struct.CompositeParser;
import dcraft.struct.CompositeStruct;
import dcraft.struct.RecordStruct;
import dcraft.task.*;
import dcraft.task.run.WorkHub;
import dcraft.task.run.WorkTopic;
import dcraft.util.IOUtil;
import dcraft.util.StringUtil;
import dcraft.util.TimeUtil;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

// longer term jobs than work pool - recoverable, retry, debuggable, etc
public class QueueHub {
	static protected AtomicLong nextid = new AtomicLong();
	
	static synchronized protected String allocateTriggerId() {
		long num = QueueHub.nextid.getAndIncrement();
		
		if (num > 9999) {
			QueueHub.nextid.set(0);
			num = 0;
		}
		
		return StringUtil.leftPad(num + "", 4, '0');
	}
	
	static public void queueGlobalTask(Task task) {
		if (! task.validate()) {
			Logger.error("Unable to queue task, invalid!");
			return;
		}
		
		// TODO add global
		Logger.error("Unable to queue task, global tasks not yet supported!");
	}
	
	static public void queueLocalTask(Task task) {
		if (! task.validate()) {
			Logger.error("Unable to queue task, invalid!");
			return;
		}
		
		String taskid = task.getId();
		
		Path workpath = ApplicationHub.getDeploymentNodePath().resolve("tasks/" + taskid);
		
		if (Files.exists(workpath)) {
			Logger.error("Task path already exists, unable to add task: " + workpath);
			return;
		}
	
		try {
			task.withTargetNodeId(ApplicationHub.getNodeId());
			
			Files.createDirectories(workpath);
			
			RecordStruct trec = task.freezeToRecord();
			
			// TODO in future try to respect the context more, for now we only support "Root" mode
			RecordStruct crec = trec.getFieldAsRecord("Context");
			RecordStruct urec = crec.getFieldAsRecord("User");
			
			trec.removeField("Context");
			
			trec.with("User", RecordStruct.record()
					.with("Mode", "Root")
					.with("Tenant", urec.getFieldAsString("Tenant"))
					.with("Site", urec.getFieldAsString("Site"))
			);
			
			IOUtil.saveEntireFile(workpath.resolve("task.json"), trec.toPrettyString());
			
			String opid = TimeUtil.stampFmt.format(task.getRunStamp())
					+ "_" + QueueHub.allocateTriggerId();
			
			Path triggpath = ApplicationHub.getDeploymentNodePath().resolve("triggers/" + opid);
			
			IOUtil.saveEntireFile(triggpath, task.getId());
		}
		catch (IOException x) {
			Logger.error("Unable to add task, error writing file: " + x);
		}
	}
	
	static public void enableQueueChecker() {
		Task peroidicChecker = Task.ofHubRoot()
				.withTitle("Review outstanding work triggers")
				.withTopic(WorkTopic.SYSTEM)
				.withNextId("QUEUE")
				.withWork(new PeriodicTriggerReviewWork());
		
		TaskHub.scheduleIn(peroidicChecker, 5);		// TODO switch to 90
		
		Task startChecker = Task.ofHubRoot()
				.withTitle("Start periodic review of outstanding work triggers")
				.withTopic(WorkTopic.SYSTEM)
				.withNextId("QUEUE")
				.withWork(new IWork() {
					@Override
					public void run(TaskContext taskctx) throws OperatingContextException {
						// sweep the triggers folder every 5 seconds
						TaskHub.scheduleEvery(peroidicChecker, 5);
						taskctx.returnEmpty();
					}
				});
		
		TaskHub.scheduleIn(startChecker, 20);			// TODO switch to 120
	}
	
	static public void attemptTrigger(Path trigger) {
		if (! Files.exists(trigger)) {
			Logger.error("Missing trigger file: " + trigger);
			return;
		}
		
		String trigid = trigger.getFileName().toString();
		
		// not a trigger file
		if (trigid.length() != 24)
			return;
		
		String trigstamp = trigid.substring(0, 19);
		
		ZonedDateTime trigtime = TimeUtil.parseDateTime(trigstamp);
		
		// trigger for future, skip for now
		if ((trigtime == null) || trigtime.isAfter(TimeUtil.now()))
			return;
		
		String taskid = IOUtil.readEntireFile(trigger).toString().trim();
		
		Path workpath = ApplicationHub.getDeploymentNodePath().resolve("tasks/" + taskid);
		
		if (! Files.exists(workpath)) {
			Logger.error("Missing work path: " + workpath);
			
			try {
				if (ApplicationHub.isProduction())
					Files.deleteIfExists(trigger);
			}
			catch (IOException x) { }
			
			return;
		}
		
		Path workinfopath = workpath.resolve("task.json");
		
		if (! Files.exists(workinfopath)) {
			Logger.error("Missing work file: " + workinfopath);
			
			try {
				if (ApplicationHub.isProduction())
					Files.deleteIfExists(trigger);
			}
			catch (IOException x) { }
			
			return;
		}
		
		CompositeStruct json = CompositeParser.parseJson(workinfopath);
		
		if ((json == null) || ! (json instanceof RecordStruct)) {
			Logger.error("Bad work file content: " + workinfopath);
			
			try {
				if (ApplicationHub.isProduction())
					Files.deleteIfExists(trigger);
			}
			catch (IOException x) { }
			
			return;
		}
		
		Task task = Task.ofWork((RecordStruct) json);
		
		if (! task.validate()) {
			Logger.error("Invalid work file content: " + workinfopath);
			
			try {
				if (ApplicationHub.isProduction())
					Files.deleteIfExists(trigger);
			}
			catch (IOException x) { }
			
			return;
		}
		
		String target = task.getTargetNodeId();
		
		if (StringUtil.isEmpty(target)) {
			// TODO add async global lock - big task
			// TODO check topic
			// if lock works then check global flag that says file was done
		}
		else if (! ApplicationHub.getNodeId().equals(target)) {
			// not for us, skip
			
			try {
				// not for DEV as we may switch hubs during testing
				if (ApplicationHub.isProduction())
					Files.deleteIfExists(trigger);
			}
			catch (IOException x) { }

			return;
		}
		
		if (! WorkHub.hasTopic(task.getTopic())) {
			try {
				// not for DEV as we may switch hubs during testing
				if (ApplicationHub.isProduction()) {
					Logger.error("Found local task, but topic mot supported: " + taskid + " - " + task.getTopic());
					Files.deleteIfExists(trigger);
				}
			}
			catch (IOException x) { }
			
			return;
		}
		
		// check lock
		CommonPath claimpath = CommonPath.from("/tasks/" + taskid);
		String claimid = ApplicationHub.makeLocalClaim(claimpath, task.getTimeout());
		
		if (StringUtil.isEmpty(claimid))
			return;			// someone else has it
		
		task.withClaimPath(claimpath.toString())
			.withClaimId(claimid);
		
		// TODO separate here on into a callback for use with global locks
		
		// fnd last run - runs should be sequentially assigned numnbers padded to 5 places
		AtomicReference<String> lastrunid = new AtomicReference<>("00000");
		
		try (Stream<Path> pathStream = Files.list(workpath)) {
			pathStream.forEach(path -> {
				String fname = path.getFileName().toString();
				
				// looking for audit files, skip if not
				if (Files.isDirectory(path) || (fname.length() < 5))
					return;
				
				// get the numbers
				fname = fname.substring(0, 5);
				
				// ensure this is numbers, if so record if highest number found
				if (StringUtil.isDataInteger(fname) && (lastrunid.get().compareTo(fname) < 0))
					lastrunid.set(fname);
			});
		}
		catch (Exception x) {
			Logger.error("Unable to list work folder");
			return;
		}

		// convert the last run id into a number
		int lastrun = 0;
		
		try {
			lastrun = Integer.parseInt(lastrunid.get());
		}
		catch (Exception x) {
		}
		
		// look at Status field of last audit, if any, to indicate job done
		if (lastrun > 0) {
			Path statuspath = workpath.resolve(lastrunid.get() + "-audit.json");
			
			if (Files.exists(statuspath)) {
				CompositeStruct statusdata = CompositeParser.parseJson(statuspath);
				
				if ((statusdata == null) || !(statusdata instanceof RecordStruct)) {
					Logger.error("Status file is bad: " + taskid);
					return;
				}
				
				String laststatus = ((RecordStruct) statusdata).getFieldAsString("Status");
				
				// done so skip this task
				if ("Success".equals(laststatus)) {
					Logger.warn("Status file for last run was success, yet still in queue: " + taskid);
					
					try {
						Files.deleteIfExists(trigger);
					}
					catch (IOException x) {
					}
					
					return;
				}
			}
			else {
				Logger.warn("Missing status file for last run: " + taskid);
			}
		}

		// start a new run id and audit
		lastrun++;
		String runid = StringUtil.leftPad(lastrun + "", 5, '0');
		Path auditpath = workpath.resolve(runid + "-audit.json");

		// create new status record for the run
		RecordStruct status = RecordStruct.record()
				.with("StartStamp", TimeUtil.now())
				.with("Trigger", trigid);
		
		// check if we have exceeded tries, if so skip this task
		if (lastrun > task.getMaxTries()) {
			IOUtil.saveEntireFile(
				auditpath,
				status
					.with("Status", "Failure")
					.with("EndStamp", TimeUtil.now())
					.with("Code", 1)
					.with("Message", "Exceeded tries")
					.toPrettyString()
			);
			
			try {
				Files.deleteIfExists(trigger);
			}
			catch (IOException x) {
			}
			
			Logger.info("Task has exceeded max tries: " + taskid);
			
			// TODO send a sysop notice about this failure
			
			return;
		}
		
		Logger.info("Starting task run: " + taskid + " : " + runid);
		
		task.withRunId(runid);

		// wrap the worker with a check to ensure that the claim is still valid before running
		task.withWork(ClaimCheckWork.of(task.buildWork()));
		
		TaskHub.submit(task,
				TaskLogger.of(task.getId())
						.withLogFile(workpath.resolve(runid + "-audit.tmp")),
				new TaskObserver() {
					@Override
					public void callback(TaskContext taskctx) {
						Logger.info("Finishing task run: " + taskctx.getTask().getId() + " : " + taskctx.getTask().getRunId());
						
						if (! ApplicationHub.updateLocalClaim(claimpath, task.getTimeout(), claimid)) {
							Logger.warn("Lost claim for last task run: " + taskid + " : " + runid);
							return;
						}
						
						status
								.with("EndStamp", TimeUtil.now());
						
						if (taskctx.hasExitErrors()) {
							status
									.with("Status", "Failure")
									.with("Code", taskctx.getExitCode())
									.with("Message", taskctx.getExitMessage());
							
							Logger.info("Task completed and failed: " + taskid);
						}
						else {
							status
									.with("Status", "Success");
							
							// remove trigger if done
							try {
								Files.deleteIfExists(trigger);
							}
							catch (IOException x) { }
							
							Logger.info("Task completed successfully: " + taskid);
						}
						
						IOUtil.saveEntireFile(auditpath, status.toPrettyString());
						
						// TODO if global update the cloud DB and create an audit deposit
					}
				});
	}
	
	// TODO review, this claim check might be inherent in the TaskContext run task code
	static public class ClaimCheckWork extends ChainWork {
		static public ClaimCheckWork of(IWork work) {
			ClaimCheckWork checkWork = new ClaimCheckWork();
			checkWork.then(work);
			return checkWork;
		}
		
		protected boolean firstrun = true;
		
		@Override
		public void run(TaskContext taskctx) throws OperatingContextException {
			// if a work queue is very long, a task might not run before the claim expires - do not proceed if so
			if (firstrun) {
				firstrun = false;
				
				if (! ApplicationHub.updateLocalClaim(CommonPath.from(taskctx.getTask().getClaimPath()), taskctx.getTask().getTimeout(), taskctx.getTask().getClaimId())) {
					Logger.warn("Lost claim for last task run: " + taskctx.getTask().getId() + " : " + taskctx.getTask().getRunId());
					taskctx.returnEmpty();
					return;
				}
				
				Logger.info("Starting task run: " + taskctx.getTask().getId() + " : " + taskctx.getTask().getRunId());
			}
			
			super.run(taskctx);
		}
	}
	
	static public class PeriodicTriggerReviewWork implements IWork {
		@Override
		public void run(TaskContext taskctx) throws OperatingContextException {
			Path trigpath = ApplicationHub.getDeploymentNodePath().resolve("triggers");
			
			try {
				if (Files.notExists(trigpath))
					Files.createDirectories(trigpath);
				
				try (Stream<Path> pathStream = Files.list(trigpath)) {
					pathStream.forEach(path -> QueueHub.attemptTrigger(path));
				}
				catch (Exception x) {
					Logger.error("Unable to list triggers folder");
				}
			}
			catch (Exception x) {
				Logger.error("Unable to access triggers folder");
			}
			
			taskctx.returnEmpty();
		}
	}
	
	/* TODO rework
	protected IQueueDriver impl = null;
	protected IQueueAlerter alerter = null;
	
	@Override
	public void init(OperationResult or, XElement config) {
		if (config == null)		// no error, it is ok to have a hub without a work queue 
			return;
		
		// setup the provider of the work queue
		String classname = config.getAttribute("InterfaceClass");
		
		if (StringUtil.isEmpty(classname)) {
			or.errorTr(173);
			return;
		}
		
		Object impl =  Hub.instance.getInstance(classname);		
		
		if ((impl == null) || !(impl instanceof IQueueDriver)) {
			or.errorTr(174, classname);
			return;
		}
		
		this.impl = (IQueueDriver)impl;
		this.impl.init(or, config);
		
		// setup the class to handle alerts
		classname = config.getAttribute("AlertClass");
		
		if (StringUtil.isNotEmpty(classname)) {
			impl =  Hub.instance.getInstance(classname);		
			
			if ((impl == null) || !(impl instanceof IQueueAlerter)) {
				or.errorTr(180, classname);
				return;
			}
			
			this.alerter = (IQueueAlerter)impl;
			this.alerter.init(or, config);
		}
		
		long qperiod = StringUtil.parseInt(config.getAttribute("CheckPeriod"), 2);
		
		ISystemWork queuechecker = new ISystemWork() {
			@Override
			public void run(SysReporter reporter) {
				reporter.setStatus("Reviewing bucket work queues");
				
				if (Hub.instance.getState() != HubState.Running) 		// only grab work when running
					return;
				
				for (WorkTopic pool : Hub.instance.getWorkPool().getTopics()) {
					if (!pool.getAutomaticQueueLoader())
						continue;
					
					int howmany = pool.availCount();    
					
					if (howmany < 1)
						continue;
					
					FuncResult<ListStruct> matches = WorkQueue.this.impl.findPotentialClaims(pool.getName(), howmany);
					
					if (matches.hasErrors()) {
						Logger.warn(matches.getMessage());
						continue;
					}
					
					ListStruct rs = matches.getResult();
					
					//System.out.print(rs.getSize() + "");
					
					for (Struct match : rs.getItems()) {
						RecordStruct rec = (RecordStruct)match;
						
						FuncResult<RecordStruct> claimop = WorkQueue.this.impl.makeClaim(rec);
						
						// ignore errors, typically means someone else got to it first
						if (claimop.hasErrors()) 
							continue;

						// replace
						rec = claimop.getResult();
						
						FuncResult<Task> loadop = WorkQueue.this.impl.loadWork(rec);
						
						// enough. should be logged, skip
						if (loadop.hasErrors())
							continue;
						
						// TODO fix dcQueue feature DCTASKLOG so we get the full builder object
						Task info = loadop.getResult();
						
						// TODO collect task objects here and watch lastActivity to update the claim
						// when updating claims, also routinely check for and update the logs in the db server?
						
						// TODO if being debugged put in session
						//Hub.instance.getSessions().createForSingleTaskAndDie(info);
						
						Hub.instance.getWorkPool().submit(info);
					}
				}
				
				reporter.setStatus("After bucket work queues");
			}

			@Override
			public int period() {
				return (int) qperiod;
			}
		};
		
		Hub.instance.getClock().addSlowSystemWorker(queuechecker);		
	}

	@Override
	public void start(OperationResult or) {
		if (this.impl != null)
			this.impl.start(or);
	}

	@Override
	public void stop(OperationResult or) {
		if (this.impl != null)
			this.impl.stop(or);
	}

	@Override
	public FuncResult<ListStruct> findPotentialClaims(String pool, int howmanymax) {
		if (this.impl != null)
			return this.impl.findPotentialClaims(pool, howmanymax);
		
		FuncResult<ListStruct> or = new FuncResult<ListStruct>();
		or.errorTr(172);
		return or;
	}
	
	@Override
	public FuncResult<RecordStruct> makeClaim(RecordStruct info) {
		if (this.impl != null)
			return this.impl.makeClaim(info);

		FuncResult<RecordStruct> or = new FuncResult<RecordStruct>();
		or.errorTr(172);
		return or;
	}
	
	@Override
	public OperationResult updateClaim(Task info) {
		if (this.impl != null)
			return this.impl.updateClaim(info);

		OperationResult or = new OperationResult();
		or.errorTr(172);
		return or;
	}
	
	public FuncResult<String> reserveUniqueAndSubmit(Task task) {
		FuncResult<String> cres = this.reserveUniqueWork(task.getId());
		
		if (cres.hasErrors())
			return cres;
		
		// if empty then assume someone else reserved it so skip (return "all is ok")
		if (cres.isEmptyResult())
			return new FuncResult<>();
		
		// we must have a claim, which means no one else can take it 
		task.withClaimedStamp(cres.getResult());
		return this.submit(task);
	}

	@Override
	public FuncResult<String> reserveUniqueWork(String taskidentity) {
		if (this.impl != null)
			return this.impl.reserveUniqueWork(taskidentity);

		FuncResult<String> or = new FuncResult<String>();
		or.errorTr(172);
		return or;
	}
	
	public FuncResult<String> reserveCurrentAndSubmit(Task task) {
		FuncResult<String> cres = this.reserveCurrentWork(task.getId());
		
		if (cres.hasErrors())
			return cres;
		
		// if empty then assume someone else reserved it so skip (return "all is ok")
		if (cres.isEmptyResult())
			return new FuncResult<>();
		
		// we must have a claim, which means no one else can take it 
		task.withClaimedStamp(cres.getResult());
		return this.submit(task);
	}

	@Override
	public FuncResult<String> reserveCurrentWork(String taskidentity) {
		if (this.impl != null)
			return this.impl.reserveCurrentWork(taskidentity);

		FuncResult<String> or = new FuncResult<String>();
		or.errorTr(172);
		return or;
	}

	@Override
	public FuncResult<String> submit(Task info) {
		info.prep();
		
		if (this.impl != null)
			return this.impl.submit(info);
		
		FuncResult<String> or = new FuncResult<>();
		or.errorTr(172);
		return or;
	}

	@Override
	public FuncResult<String> startWork(String workid) {
		if (this.impl != null)
			return this.impl.startWork(workid);
		
		FuncResult<String> or = new FuncResult<>();
		or.errorTr(172);
		return or;
	}

	public FuncResult<Task> loadWork(RecordStruct info) {
		if (this.impl != null)
			return this.impl.loadWork(info);
		
		FuncResult<Task> or = new FuncResult<>();
		or.errorTr(172);
		return or;
	}
	
	public OperationResult failWork(TaskRun task)  {
		task.getTask().withStatus("Failed");
		
		if (this.impl != null)
			return this.impl.endWork(task);
		
		OperationResult or = new OperationResult();
		or.errorTr(172);
		return or;
	}

	public OperationResult completeWork(TaskRun task) {
		// if work is complete, it is the final try
		task.getTask()
			.withFinalTry(true)
			.withStatus("Completed");
		
		if (this.impl != null)
			return this.impl.endWork(task);

		OperationResult or = new OperationResult();
		or.errorTr(172);
		return or;
	}

	@Override
	public OperationResult endWork(TaskRun task) {
		if (this.impl != null)
			return this.impl.endWork(task);
		
		OperationResult or = new OperationResult();
		or.errorTr(172);
		return or;
	}

	@Override
	public OperationResult trackWork(TaskRun task, boolean ended) {
		if (this.impl != null)
			return this.impl.trackWork(task, ended);
		
		OperationResult or = new OperationResult();
		or.errorTr(172);
		return or;
	}

	@Override
	public void sendAlert(long code, Object... params) {
		if (this.alerter != null)
			this.alerter.sendAlert(code, params);
	}

	@Override
	public ListStruct list() {
		if (this.impl != null)
			return this.impl.list();
		
		return null;
	}

	@Override
	public RecordStruct status(String taskid, String workid) {
		if (this.impl != null)
			return this.impl.status(taskid, workid);
		
		return null;
	}
	*/
}
