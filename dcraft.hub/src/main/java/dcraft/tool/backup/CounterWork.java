package dcraft.tool.backup;

import dcraft.hub.app.ApplicationHub;
import dcraft.hub.op.OperatingContextException;
import dcraft.log.count.CountHub;
import dcraft.log.count.NumberCounter;
import dcraft.struct.RecordStruct;
import dcraft.task.IWork;
import dcraft.task.TaskContext;
import dcraft.util.FileUtil;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.math.BigDecimal;
import java.util.List;

public class CounterWork implements IWork {
	@Override
	public void run(TaskContext taskctx) throws OperatingContextException {
		// if there are counters then pull out the key ones
		if (CountHub.getCounter("javaSystemLoadAverage") != null) {
			BigDecimal heapCommitted = ((NumberCounter) CountHub.getCounter("javaMemoryHeapCommitted")).getValue();
			BigDecimal heapUsed = ((NumberCounter) CountHub.getCounter("javaMemoryHeapUsed")).getValue();
			BigDecimal heapUsedHigh = ((NumberCounter) CountHub.getCounter("javaMemoryHeapUsed")).getHigh();
			
			BigDecimal nonHeapCommitted = ((NumberCounter) CountHub.getCounter("javaMemoryNonHeapCommitted")).getValue();
			BigDecimal nonHeapUsed = ((NumberCounter) CountHub.getCounter("javaMemoryNonHeapUsed")).getValue();
			BigDecimal nonHeapUsedHigh = ((NumberCounter) CountHub.getCounter("javaMemoryNonHeapUsed")).getHigh();
			
			BigDecimal garbageCollects = ((NumberCounter) CountHub.getCounter("javaGarbageCollects")).getValue();
			BigDecimal garbageTime = ((NumberCounter) CountHub.getCounter("javaGarbageTime")).getValue();
			BigDecimal garbageTimeHigh = ((NumberCounter) CountHub.getCounter("javaGarbageTime")).getHigh();
			
			BigDecimal loadAverage = ((NumberCounter) CountHub.getCounter("javaSystemLoadAverage")).getValue();
			BigDecimal loadAverageHigh = ((NumberCounter) CountHub.getCounter("javaSystemLoadAverage")).getHigh();
			
			CountHub.resetReturnCounters();
			
			String report = "Heap Memory: U " + FileUtil.formatFileSize(heapUsed.longValue()) + " / H "
					+ FileUtil.formatFileSize(heapUsedHigh.longValue()) + " / C " + FileUtil.formatFileSize(heapCommitted.longValue())
					+ " - Non Heap: U " + FileUtil.formatFileSize(nonHeapUsed.longValue()) + " / H "
					+ FileUtil.formatFileSize(nonHeapUsedHigh.longValue()) + " / C " + FileUtil.formatFileSize(nonHeapCommitted.longValue())
					+ " / Sys " + FileUtil.formatFileSize(Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) + "\n"
					+ "GC: #" + garbageCollects.toPlainString() + " / " + garbageTime.toPlainString() + " / " + garbageTimeHigh.toPlainString() + '\n'
					+ "Load: " + loadAverage.toPlainString() + " / " + loadAverageHigh.toPlainString();

			// TODO ProcessHandle.current()
			
			/*
			collect system ram use too
			
			[ec2-user@ip-172-31-22-51 dcserver]$ free -m
						  total        used        free      shared  buff/cache   available
			Mem:          15285         917       11926           0        2440       14021
			Swap:             0           0           0
			
			 */

			BackupUtil.notifyProgress(ApplicationHub.getDeployment() + " : " + ApplicationHub.getNodeId() + " : Counters\n" + report);
		}
		
		taskctx.returnEmpty();
	}
}
