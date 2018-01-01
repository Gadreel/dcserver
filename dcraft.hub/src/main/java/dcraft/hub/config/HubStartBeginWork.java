package dcraft.hub.config;

import dcraft.hub.app.ApplicationHub;
import dcraft.hub.app.HubState;
import dcraft.hub.resource.ConfigResource;
import dcraft.hub.resource.ResourceTier;
import dcraft.log.HubLog;
import dcraft.log.Logger;
import dcraft.task.TaskContext;
import dcraft.util.ISettingsObfuscator;
import dcraft.util.StringUtil;
import dcraft.xml.XElement;
import org.bouncycastle.jce.provider.BouncyCastleProvider;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.security.Security;

/**
 */
public class HubStartBeginWork extends CoreLoaderWork {
	@Override
	public void firstload(TaskContext taskctx, ResourceTier tier) {
		ApplicationHub.setState(HubState.Booting);
		
		Security.addProvider(new BouncyCastleProvider());
		
		ConfigResource config = tier.getConfig();
		
		// change to overrides if find config
		XElement logger = config.getTag("Logger");
		
		// prepare the logger - use files, use custom log writer
		if (! HubLog.init(logger)) {
			Logger.error("Unable to initialize Logger");
			taskctx.returnEmpty();
			return;
		}
		
		taskctx.withDebugLevel(HubLog.getGlobalLevel());
		
		Logger.boundary("Origin", "hub:", "Op", "Start");
		
		// TODO use translation codes for all start up messages after dictionaries are loaded
		Logger.info( "Hub deployment: " + ApplicationHub.getDeployment());
		Logger.info( "Hub role: " + ApplicationHub.getRole());
		Logger.info( "Hub id: " + ApplicationHub.getNodeId());
		Logger.info( "Is hub production: " + ApplicationHub.isProduction());
		
		Logger.info( "Java version: " + System.getProperty("java.version"));
		Logger.info( "Java vendor: " + System.getProperty("java.vendor"));
		Logger.info( "Java vm: " + System.getProperty("java.vm.name"));
		
		if (Logger.isDebug()) {
			Logger.debug( "Java class path: " + System.getProperty("java.class.path"));
			Logger.debug( "Java home: " + System.getProperty("java.home"));
			Logger.debug("OS: " + System.getProperty("os.name"));
			Logger.debug( "OS Ver: " + System.getProperty("os.version"));
			Logger.debug( "OS Arch: " + System.getProperty("os.arch"));
			Logger.debug("User: " + System.getProperty("user.name"));
			Logger.debug("User working dir: " + System.getProperty("user.dir"));
		}
		
		// TODO prepare the basics, then do a prep task, then done booting
		
		try {
			ApplicationHub.setWatcher(FileSystems.getDefault().newWatchService());
		}
		catch (IOException x) {
			Logger.error("Cannot create file watcher service: " + x);
			taskctx.returnEmpty();
			return;
		}
        
        taskctx.returnEmpty();
	}
	
	@Override
	public void reload(TaskContext taskctx, ResourceTier tier) {
		Logger.boundary("Origin", "hub:", "Op", "Reload");
	
		taskctx.returnEmpty();
	}
}
