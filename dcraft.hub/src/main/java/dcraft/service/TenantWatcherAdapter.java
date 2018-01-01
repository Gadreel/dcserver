package dcraft.service;

import java.nio.file.Files;
import java.nio.file.Path;

import dcraft.log.Logger;
import dcraft.tenant.Tenant;
import dcraft.util.groovy.GCompClassLoader;

public class TenantWatcherAdapter {
	protected Path tenantpath = null;
	/* groovy
	protected GroovyObject script = null;
	*/
	
	public TenantWatcherAdapter(Path dpath) {
		this.tenantpath = dpath;
	}
	
	/* groovy
	public GroovyObject getScript() {
		return this.script;
	}
	*/

	public void init(Tenant domaininfo) {
	/* groovy
		if (this.script != null) {
			GCompClassLoader.tryExecuteMethod(this.script, "Kill", domaininfo);
			this.script = null;
		}
		
		Path cpath = this.tenantpath.resolve("config");

		if (Files.notExists(cpath))
			return;
		
		Path spath = cpath.resolve("Watcher.groovy");
		
		if (Files.notExists(spath))
			return;
		
		try {
			Class<?> groovyClass = domaininfo.getRootSite().getScriptLoader().toClass(spath);
			
			this.script = (GroovyObject) groovyClass.newInstance();
			
			GCompClassLoader.tryExecuteMethod(this.script, "Init", domaininfo);
		}
		catch (Exception x) {
			Logger.error("Unable to prepare domain watcher scriptold: " + spath);
			Logger.error("Error: " + x);
		}
		*/
	}
	
	public boolean tryExecuteMethod(String name, Object... params) {
	/* groovy
		return GCompClassLoader.tryExecuteMethod(this.script, name, params);
		*/
	
		return false;
	}
}
