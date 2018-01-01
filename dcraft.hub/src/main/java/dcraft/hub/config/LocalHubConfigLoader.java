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
package dcraft.hub.config;

import dcraft.hub.app.ApplicationHub;
import dcraft.hub.resource.ConfigResource;
import dcraft.hub.resource.ResourceTier;
import dcraft.log.Logger;
import dcraft.task.TaskContext;
import dcraft.xml.XElement;
import dcraft.xml.XmlReader;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Within dcFramework all features are tied together through the Hub class.  To get 
 * the hub going you need to give it access to some resources such as Schema
 * and config.  
 *  
 *  HubResources is the class that ties all the resources together and enables
 *  Hub to start.  A typical application using dcFramework will start up something
 *  like this: 
 *  
 *  TODO
 *			
 *	[TODO add link to Quick Start ]
 *			
 *	[TODO add link to Framework Architecture ]
 *
 * 
 * TODO consider an option where all config is loaded from AWS.  Their description:
 * 
 * You can use this data to build more generic AMIs that can be modified by configuration files supplied at launch 
 * time. For example, if you run web servers for various small businesses, they can all use the same AMI and retrieve 
 * their content from the Amazon S3 bucket you specify at launch. To add a new customer at any time, simply create a 
 * bucket for the customer, add their content, and launch your AMI.
 *
 *  
 * @author Andy
 *
 */
public class LocalHubConfigLoader extends LocalConfigLoader {
	public static LocalHubConfigLoader local() {
		return LocalHubConfigLoader.local(".");
	}

	public static LocalHubConfigLoader local(String path) {
		LocalHubConfigLoader res = new LocalHubConfigLoader();
		res.configpath = Paths.get(path);
		return res;
	}
	
	protected Path configpath = null;
	
	protected LocalHubConfigLoader() {
	}
	
	/**
	 * Initialize this object by loading the Schema, Dictionary, Config and Fabric.
	 */
	
	@Override
	public void firstload(TaskContext taskctx, ResourceTier resources) {
		// -----------------------------------
		//   load app config from local files
		// -----------------------------------
		
		ConfigResource configres = resources.getOrCreateTierConfig();
		
		Logger.infoTr(0, "Loading hub resources");
		
		Logger.traceTr(0, "Loading shared config");
		
		this.addConfigIfPresent(configres, this.resolvePath(Paths.get("config.xml")));				// the more internal aspects of config
		
		// TODO reconsider - this.addConfigIfPresent(configres, "deployment.xml");			// the more public aspects of config, treat as unsecure
		
		this.addConfigIfPresent(configres, this.resolveRolePath(Paths.get("config.xml")));			// config specific to the role
		
		this.addConfigIfPresent(configres, this.resolveNodePath(Paths.get("config.xml")));		// config specific to the node
		
		// TODO load Clock Xml from http://169.254.169.254/latest/user-data
		// then over write
		//this.config.find("Clock").replace(parsed awssource);
		
		this.initResources(taskctx, resources);
		
		taskctx.returnEmpty();
	}
	
	@Override
	public Path resolvePath(Path p) {
		return this.configpath.resolve("deploy-" + ApplicationHub.getDeployment() + "/config").resolve(p);
	}
	
	@Override
	public Path resolveRolePath(Path p) {
		// TODO cascade the roles
		return this.configpath.resolve("deploy-" + ApplicationHub.getDeployment() + "/roles/"
				+ ApplicationHub.getRole() + "/config").resolve(p);
	}
	
	@Override
	public Path resolveNodePath(Path p) {
		return this.configpath.resolve("deploy-" + ApplicationHub.getDeployment() + "/nodes/"
				+ ApplicationHub.getNodeId() + "/config").resolve(p);
	}
	
	public void addConfigIfPresent(ConfigResource configres, Path path) {
		// this is fine, not all config
		if (path == null)
			return;

		Logger.trace("Resolving config xml file: " + path.getFileName());
		
		//Path fshared = this.resolvePath(Paths.get(name));
		
		if (Files.exists(path)) {
			XElement cel = XmlReader.loadFile(path, false, true);
			
			if (cel == null) {
				Logger.error("Unable to load config file, expected: " + path);
			}
			else {
				Logger.trace("Loaded config xml file: " + path);
				
				configres.add(cel);
			}
		}
	}
	
	@Override
	public void reload(TaskContext taskctx, ResourceTier tier) {
		this.firstload(taskctx, tier);
	}
	
	/**
	 * TODO expand this idea so that project resources could be loaded from FileStore
	 *
	 * Get a reference to a resource file specific for this Project.
	 *
	 * @param filename name of the file, path relative to the resources/ folder in config
	 * @return Path reference if found, if not error messages in FuncResult
	 *
	public Path getProjectResource(String filename) {
		Path f = this.configpath.resolve(ApplicationHub.getDeployment() + "/resources/" + filename);
		
		if (Files.exists(f))
			return f;
		
		Logger.errorTr(201, f.toString());
		
		return null;
	}
	*/
	
	/**
	 * Scan through the local repository or config directory and reload the dictionary
	 * files.  After this any translation used will have updates from the dictionary files.
	 * This method is really only useful in development mode and is not typically called 
	 * by application code.
	 *   
	 * @return messages logged while reloading dictionary
	 *
	public void reloadDictionary() {
		Logger.traceTr(0, "Loading Dictionary");
		
		ResourceHub.getTopResources().getLocale().setLocalDictionary(this.getDictionary());
	}
	*/
}
