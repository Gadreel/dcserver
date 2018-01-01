package dcraft.script.work;

import dcraft.hub.config.CoreLoaderWork;
import dcraft.hub.resource.ResourceTier;
import dcraft.script.ScriptResource;
import dcraft.task.TaskContext;
import dcraft.xml.XElement;

/**
 */
public class ScriptStartWork extends CoreLoaderWork {
	@Override
	public void firstload(TaskContext taskctx, ResourceTier tier) {
		ScriptResource res = tier.getOrCreateTierScripts();
		
		//for (XElement config : tier.getConfig().getTagListDeep("Script/Instruction"))
		//	res.loadInstruction(config);
		
		for (XElement config : tier.getConfig().getTagListDeep("Script/Operation"))
			res.loadOperation(config);
        
        taskctx.returnEmpty();
	}
	
	@Override
	public void reload(TaskContext taskctx, ResourceTier tier) {
		this.firstload(taskctx, tier);
	}
}
