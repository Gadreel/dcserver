package dcraft.hub.config;

import dcraft.db.request.common.RequestFactory;
import dcraft.hub.ResourceHub;
import dcraft.hub.app.ApplicationHub;
import dcraft.hub.app.HubState;
import dcraft.hub.clock.ISystemWork;
import dcraft.hub.clock.SysReporter;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationOutcomeStruct;
import dcraft.hub.resource.ResourceTier;
import dcraft.log.Logger;
import dcraft.log.count.CountHub;
import dcraft.service.ServiceHub;
import dcraft.struct.BaseStruct;
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

import java.lang.management.ClassLoadingMXBean;
import java.lang.management.CompilationMXBean;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.OperatingSystemMXBean;
import java.lang.management.RuntimeMXBean;
import java.lang.management.ThreadMXBean;
import java.time.Instant;
import java.util.List;

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
								public void callback(BaseStruct result) throws OperatingContextException {
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

		ISystemWork monitorcounters = new ISystemWork() {
			@Override
			public void run(SysReporter reporter) {
				reporter.setStatus("Updating hub counters");
				
		        /* TODO
				SessionHub.recordCounters();
				*/
				
				if (! CountHub.hasCounter("dcRunId"))
					CountHub.allocateSetStringCounter("dcRunId", ApplicationHub.getRunId());
				
				//long st = System.currentTimeMillis();
				
				ClassLoadingMXBean clbean = ManagementFactory.getClassLoadingMXBean();
				
				CountHub.allocateSetNumberCounter("javaClassCount", clbean.getLoadedClassCount());
				CountHub.allocateSetNumberCounter("javaClassLoads", clbean.getTotalLoadedClassCount());
				CountHub.allocateSetNumberCounter("javaClassUnloads", clbean.getUnloadedClassCount());
				
				CompilationMXBean cpbean = ManagementFactory.getCompilationMXBean();

				if (cpbean != null)
					CountHub.allocateSetNumberCounter("javaCompileTime", cpbean.getTotalCompilationTime());
				
				MemoryMXBean mebean = ManagementFactory.getMemoryMXBean();
				
				CountHub.allocateSetNumberCounter("javaMemoryHeapCommitted", mebean.getHeapMemoryUsage().getCommitted());
				CountHub.allocateSetNumberCounter("javaMemoryHeapUsed", mebean.getHeapMemoryUsage().getUsed());
				CountHub.allocateSetNumberCounter("javaMemoryHeapInit", mebean.getHeapMemoryUsage().getInit());
				CountHub.allocateSetNumberCounter("javaMemoryHeapMax", mebean.getHeapMemoryUsage().getMax());
				CountHub.allocateSetNumberCounter("javaMemoryNonHeapCommitted", mebean.getNonHeapMemoryUsage().getCommitted());
				CountHub.allocateSetNumberCounter("javaMemoryNonHeapUsed", mebean.getNonHeapMemoryUsage().getUsed());
				CountHub.allocateSetNumberCounter("javaMemoryNonHeapInit", mebean.getNonHeapMemoryUsage().getInit());
				CountHub.allocateSetNumberCounter("javaMemoryNonHeapMax", mebean.getNonHeapMemoryUsage().getMax());
				CountHub.allocateSetNumberCounter("javaMemoryFinals", mebean.getObjectPendingFinalizationCount());
				
				List<GarbageCollectorMXBean> gcbeans = ManagementFactory.getGarbageCollectorMXBeans();
				long collects = 0;
				long collecttime = 0;
				
				for (GarbageCollectorMXBean gcbean : gcbeans) {
					collects += gcbean.getCollectionCount();
					collecttime += gcbean.getCollectionTime();
				}
				
				CountHub.allocateSetNumberCounter("javaGarbageCollects", collects);
				CountHub.allocateSetNumberCounter("javaGarbageTime", collecttime);
				
				OperatingSystemMXBean osbean = ManagementFactory.getOperatingSystemMXBean();
				
				CountHub.allocateSetNumberCounter("javaSystemLoadAverage", osbean.getSystemLoadAverage());
				
				RuntimeMXBean rtbean = ManagementFactory.getRuntimeMXBean();
				
				CountHub.allocateSetNumberCounter("javaJvmUptime", rtbean.getUptime());
				
				ThreadMXBean thbean = ManagementFactory.getThreadMXBean();
				
				CountHub.allocateSetNumberCounter("javaJvmRunningDaemonThreads", thbean.getDaemonThreadCount());
				CountHub.allocateSetNumberCounter("javaJvmRunningPeakThreads", thbean.getPeakThreadCount());
				CountHub.allocateSetNumberCounter("javaJvmRunningThreads", thbean.getThreadCount());
				CountHub.allocateSetNumberCounter("javaJvmStartedThreads", thbean.getTotalStartedThreadCount());
				
				//System.out.println("collect: " + (System.currentTimeMillis() - st));
				
				//System.out.println("reply count: " + Hub.getCountManager().getCounter("dcBusReplyHandlers"));
				
				reporter.setStatus("After reviewing hub counters");
			}

			@Override
			public int period() {
				return 300;
			}
		};
		
		ApplicationHub.getClock().addSlowSystemWorker(monitorcounters);
		
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
