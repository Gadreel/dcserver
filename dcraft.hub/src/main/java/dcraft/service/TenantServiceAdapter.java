package dcraft.service;

import dcraft.hub.ResourceHub;

import java.nio.file.Path;

import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
import dcraft.hub.op.OperationOutcomeStruct;
import dcraft.util.StringUtil;
import dcraft.xml.XElement;

public class TenantServiceAdapter extends BaseService {
	protected String name = null;	// TODO rename, not service name
	protected Path sourcepath = null;
	protected Path domainpath = null;
	protected XElement settings = null;
	
	protected IService java = null;
	/* groovy
	protected Map<String, ServiceFeature> features = new HashMap<String, ServiceFeature>();
	*/
	
	public TenantServiceAdapter(String name, Path spath, Path dpath, XElement settings) throws OperatingContextException {
		this.name = name;
		this.sourcepath = spath;
		this.domainpath = dpath;
		this.settings = settings;
		
		if (settings != null) {
			String runclass = settings.getAttribute("RunClass");
			
			if (StringUtil.isNotEmpty(runclass)) {
				this.java = (IService) ResourceHub.getResources().getClassLoader().getInstance(runclass);  // TODO myabe tier if this is called from service start
				
				this.java.init(settings.getAttribute("Name"), this.settings, OperationContext.getOrThrow().getResources());
			}
		}
	}
	
	/* groovy
	public GroovyObject getScript(Tenant domain, String name) {
		ServiceFeature f = this.getFeature(domain, name);
		
		if (f != null) 
			return f.script;
		
		return null;
	}
	
	public ServiceFeature getFeature(Tenant domain, String name) {
		ServiceFeature f = this.features.get(name);
		
		if (f == null) {
			f = new ServiceFeature(domain, name);
			this.features.put(name, f);
		}
		
		return f;
	}
	*/
	
	@Override
	public boolean handle(ServiceRequest request, OperationOutcomeStruct callback) throws OperatingContextException {
		// try to handle in java if possible
		if ((this.java != null) && this.java.handle(request, callback)) {
			System.out.println("Service call handled by Java");
			return true;
		}
		
	/* groovy
		ServiceFeature f = this.getFeature(OperationContext.getOrThrow().getTenant(), request.getFeature());
		
		if (f != null)
			return f.handle(request, callback);
			*/
		
		return false;
	}
	
	/* groovy
	public class ServiceFeature {
		protected GroovyObject script = null;
		protected String feature = null;
		
		public ServiceFeature(Tenant domain, String feature) {
			this.feature = feature;
			
			Path spath = TenantServiceAdapter.this.sourcepath.resolve(feature + ".groovy");
			
			if (Files.notExists(spath))
				return;
			
			try {
				Class<?> groovyClass = domain.getRootSite().getScriptLoader().toClass(spath);
				
				this.script = (GroovyObject) groovyClass.newInstance();
			}
			catch (Exception x) {
				Logger.error("Unable to prepare service scriptold: " + spath);
				Logger.error("Error: " + x);
			}		
		}
		
		public boolean handle(ServiceRequest request, OperationOutcomeStruct callback) {
			if (this.script == null) {
				Logger.errorTr(441, this.feature);
				callback.returnResult();
				return false;
			}
			
			if (! GCompClassLoader.tryExecuteMethod(this.script, request.getOp(), request, callback)) {
				Logger.errorTr(441, this.feature);
				callback.returnResult();
				return false;
			}
			
			return true;
		}
	}
	*/
}
