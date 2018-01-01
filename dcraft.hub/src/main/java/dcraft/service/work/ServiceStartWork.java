package dcraft.service.work;

import dcraft.hub.config.CoreLoaderWork;
import dcraft.hub.resource.ResourceTier;
import dcraft.log.Logger;
import dcraft.service.IService;
import dcraft.service.ServiceResource;
import dcraft.task.TaskContext;
import dcraft.util.StringUtil;
import dcraft.xml.XElement;

import java.util.List;

/**
 */
public class ServiceStartWork extends CoreLoaderWork {
	@Override
	public void firstload(TaskContext taskctx, ResourceTier tier) {
		ServiceResource srres = tier.getOrCreateTierServices();

		List<XElement> services = tier.getOrCreateTierConfig().getTagListDeep("Service");
		
		for (XElement el : services) {
			try {
				String name = el.getAttribute("Name");
				
				if (StringUtil.isNotEmpty(name)) {
					IService srv = (IService) tier.getClassLoader().getInstance(el.getAttribute("RunClass"));
				
					if (srv != null) {
						srv.init(name, el, tier);
						srres.registerTierService(name, srv);
					}
				}
			}
			catch (Exception x) {
				Logger.error("Unable to load serivce: " + el);
			}
		}
		
        taskctx.returnEmpty();
	}
	
	@Override
	public void reload(TaskContext taskctx, ResourceTier tier) {
		this.firstload(taskctx, tier);
	}
}
