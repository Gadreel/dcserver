package dcraft.hub.config;

import dcraft.db.request.common.RequestFactory;
import dcraft.hub.ResourceHub;
import dcraft.hub.app.ApplicationHub;
import dcraft.hub.app.HubState;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationOutcomeStruct;
import dcraft.hub.resource.ResourceTier;
import dcraft.log.Logger;
import dcraft.service.ServiceHub;
import dcraft.struct.Struct;
import dcraft.task.IWork;
import dcraft.task.Task;
import dcraft.task.TaskContext;
import dcraft.task.TaskHub;
import dcraft.task.queue.QueueHub;
import dcraft.task.run.WorkTopic;
import dcraft.task.scheduler.ScheduleHub;
import dcraft.task.scheduler.common.CommonSchedule;
import dcraft.util.TimeUtil;
import dcraft.xml.XElement;

import java.time.Instant;

/**
 */
public class HubStartFinalWork extends CoreLoaderWork {
	@Override
	public void firstload(TaskContext taskctx, ResourceTier tier) {
		if (ResourceHub.getResources().getDatabases().hasDefaultDatabase()) {
			Task dbCleaner = Task.ofHubRoot()
					.withTitle("Clean temporary/expiring values from database")
					.withTopic(WorkTopic.SYSTEM)
					.withNextId("QUEUE")
					.withTimeout(5)
					.withWork(new IWork() {
						@Override
						public void run(TaskContext taskctx) throws OperatingContextException {
							ServiceHub.call(RequestFactory.cleanDatabaseRequest(TimeUtil.now().minusHours(72)), new OperationOutcomeStruct() {
								@Override
								public void callback(Struct result) throws OperatingContextException {
									taskctx.returnEmpty();
								}
							});
						}
					});
			
			
			TaskHub.scheduleEvery(dbCleaner, 60 * 60);  // once an hour
			
			//TaskHub.scheduleIn(dbCleaner, 15);  //
		}
		
	/*

		ISystemWork filefolderwatcher = new ISystemWork() {
			@SuppressWarnings("unchecked")
			@Override
			public void run(SysReporter reporter) {
			    // wait for key to be signaled
			    WatchKey key = ApplicationHub.watcher.poll();
			    
			    if (key == null)
			    	return;
			    
			    //System.out.println("Watch key count: " + Hub.filewatchdata.size());
			    
		        IFileWatcher fw = ApplicationHub.filewatchdata.get(key);
			    
			    if (fw == null)
			    	return;
		      
			    for (WatchEvent<?> event: key.pollEvents()) {
			        WatchEvent.Kind<?> kind = event.kind();

					WatchEvent<Path> ev = (WatchEvent<Path>)event;
			        Path filename = ev.context();

			        System.out.println("detected " + filename + " : " + kind);
			        
			        fw.fireFolderEvent(filename, (Kind<Path>) kind);
			    }

			    // Reset the key -- this step is critical if you want to
			    // receive further watch events.  If the key is no longer valid,
			    // the directory is inaccessible so exit the loop.
			    if (! key.reset())
			    	ApplicationHub.filewatchdata.remove(key);
			}

			@Override
			public int period() {
				return 1;
			}
		};
		
		ApplicationHub.clock.addSlowSystemWorker(filefolderwatcher);
		
		// TODO review if this even works...
    	// TODO remember that sys workers should not use OperationContext
		// every five minutes run cleanup to remove expired temp files
		// also cleanup hub/default operating contexts
        /* TODO
		ISystemWork cleanexpiredtemp = new ISystemWork() {
				@Override
				public void run(SysReporter reporter) {
					reporter.setStatus("Cleaning contexts and temp files");
					
					if (! Hub.isStopping())  {
						// TODO turn all this into a job and give it a OpContext - since we shouldn't have one here
						
						FileUtil.cleanupTemp();
					}
					
					reporter.setStatus("After cleaning contexts and temp files");
				}

				@Override
				public int period() {
					return 300;
				}
		};
		
		Hub.clock.addSlowSystemWorker(cleanexpiredtemp);
		*/
		
		// TODO remember that sys workers should not use OperationContext
		// monitor the Hub/Java/Core counters
        /* TODO
		ISystemWork monitorcounters = new ISystemWork() {
			@Override
			public void run(SysReporter reporter) {
				reporter.setStatus("Updating hub counters");
				
				CountManager cm = Hub.getCountManager();
				
		        /* TODO
				SessionHub.recordCounters();
				* /
				
				if (! cm.hasCounter("dcRunId"))
					cm.allocateSetStringCounter("dcRunId", Hub.getRunId());
				
				//long st = System.currentTimeMillis();
				
				ClassLoadingMXBean clbean = ManagementFactory.getClassLoadingMXBean();
				
				cm.allocateSetNumberCounter("javaClassCount", clbean.getLoadedClassCount());
				cm.allocateSetNumberCounter("javaClassLoads", clbean.getTotalLoadedClassCount());
				cm.allocateSetNumberCounter("javaClassUnloads", clbean.getUnloadedClassCount());
				
				CompilationMXBean cpbean = ManagementFactory.getCompilationMXBean();

				if (cpbean != null)
					cm.allocateSetNumberCounter("javaCompileTime", cpbean.getTotalCompilationTime());
				
				MemoryMXBean mebean = ManagementFactory.getMemoryMXBean();
				
				cm.allocateSetNumberCounter("javaMemoryHeapCommitted", mebean.getHeapMemoryUsage().getCommitted());
				cm.allocateSetNumberCounter("javaMemoryHeapUsed", mebean.getHeapMemoryUsage().getUsed());
				cm.allocateSetNumberCounter("javaMemoryHeapInit", mebean.getHeapMemoryUsage().getInit());
				cm.allocateSetNumberCounter("javaMemoryHeapMax", mebean.getHeapMemoryUsage().getMax());
				cm.allocateSetNumberCounter("javaMemoryNonHeapCommitted", mebean.getNonHeapMemoryUsage().getCommitted());
				cm.allocateSetNumberCounter("javaMemoryNonHeapUsed", mebean.getNonHeapMemoryUsage().getUsed());
				cm.allocateSetNumberCounter("javaMemoryNonHeapInit", mebean.getNonHeapMemoryUsage().getInit());
				cm.allocateSetNumberCounter("javaMemoryNonHeapMax", mebean.getNonHeapMemoryUsage().getMax());
				cm.allocateSetNumberCounter("javaMemoryFinals", mebean.getObjectPendingFinalizationCount());
				
				List<GarbageCollectorMXBean> gcbeans = ManagementFactory.getGarbageCollectorMXBeans();
				long collects = 0;
				long collecttime = 0;
				
				for (GarbageCollectorMXBean gcbean : gcbeans) {
					collects += gcbean.getCollectionCount();
					collecttime += gcbean.getCollectionTime();
				}
				
				cm.allocateSetNumberCounter("javaGarbageCollects", collects);
				cm.allocateSetNumberCounter("javaGarbageTime", collecttime);
				
				OperatingSystemMXBean osbean = ManagementFactory.getOperatingSystemMXBean();
				
				cm.allocateSetNumberCounter("javaSystemLoadAverage", osbean.getSystemLoadAverage());
				
				RuntimeMXBean rtbean = ManagementFactory.getRuntimeMXBean();
				
				cm.allocateSetNumberCounter("javaJvmUptime", rtbean.getUptime());
				
				ThreadMXBean thbean = ManagementFactory.getThreadMXBean();
				
				cm.allocateSetNumberCounter("javaJvmRunningDaemonThreads", thbean.getDaemonThreadCount());
				cm.allocateSetNumberCounter("javaJvmRunningPeakThreads", thbean.getPeakThreadCount());
				cm.allocateSetNumberCounter("javaJvmRunningThreads", thbean.getThreadCount());
				cm.allocateSetNumberCounter("javaJvmStartedThreads", thbean.getTotalStartedThreadCount());
				
				//System.out.println("collect: " + (System.currentTimeMillis() - st));
				
				//System.out.println("reply count: " + Hub.getCountManager().getCounter("dcBusReplyHandlers"));
				
				reporter.setStatus("After reviewing hub counters");
			}

			@Override
			public int period() {
				return 300;
			}
		};
		
		Hub.getClock().addSlowSystemWorker(monitorcounters);
		*/
		
		ApplicationHub.setState(HubState.Booted);
		
		Logger.boundary("Origin", "hub:", "Op", "Run");
        
        taskctx.returnEmpty();
	}
	
	@Override
	public void reload(TaskContext taskctx, ResourceTier tier) {
		Logger.boundary("Origin", "hub:", "Op", "Reloaded");
		
		taskctx.returnEmpty();
	}
}
