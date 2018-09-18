package dcraft.task.run;

import dcraft.hub.app.ApplicationHub;
import dcraft.hub.config.CoreLoaderWork;
import dcraft.hub.resource.ResourceTier;
import dcraft.log.Logger;
import dcraft.log.count.CountHub;
import dcraft.task.TaskContext;
import dcraft.xml.XElement;

public class StandardWorkStart extends CoreLoaderWork {
	@Override
	public void firstload(TaskContext taskctx, ResourceTier tier) {
		Logger.debug("Initializing Work Hub");
		
		XElement config = tier.getConfig().getTag("WorkHub");
		
		if (config == null)
			config = XElement.tag("WorkHub");
		
		if (config == null)
			config = XElement.tag("WorkHub");
		
		Long size = config.getAttributeAsInteger("Threads", 16);
		
		WorkHub.topics.clear();
		
		// place the default topic in - it might be overridden in config
		WorkHub.addTopic(WorkTopic.of(WorkTopic.DEFAULT, null, size));
		
		// the system topic is always exactly 1 to prevent more than one at a time
		WorkHub.addTopic(WorkTopic.of(WorkTopic.SYSTEM, 1L, 1L));
		
		for (XElement topicel : config.selectAll("Topic"))
			WorkHub.addTopic(WorkTopic.of(topicel, size));
		
		if (taskctx.hasExitErrors()) {
			Logger.error("Unable to start Work Hub");
			taskctx.returnEmpty();
			return;
		}
		
		CountHub.allocateSetNumberCounter("dcWorkPool_Topics", WorkHub.topics.size());
		
		CountHub.allocateSetNumberCounter("dcWorkPool_Threads", size);
		
		WorkHub.resizeSlots(size.intValue());
		
		if (! WorkHub.sysworker) {
			ApplicationHub.getClock().addSlowSystemWorker(new HungWorkSysWorker());
			WorkHub.sysworker = true;
		}
		
		taskctx.returnEmpty();
	}
	
	@Override
	public void reload(TaskContext taskctx, ResourceTier tier) {
		this.firstload(taskctx, tier);
	}
}
