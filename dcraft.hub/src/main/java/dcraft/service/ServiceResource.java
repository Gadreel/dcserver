package dcraft.service;

import java.util.*;

import dcraft.hub.op.OperatingContextException;
import dcraft.hub.resource.ResourceBase;
import dcraft.hub.resource.ResourceTier;
import dcraft.log.Logger;

public class ServiceResource extends ResourceBase {
	protected Map<String, IService> registered = new HashMap<>();
	protected List<String> serviceorder = new ArrayList<>();
	
	public ServiceResource() {
		this.setName("Service");
	}
	
	public ServiceResource getParentResource() {
		if (this.tier == null)
			return null;
		
		ResourceTier pt = this.tier.getParent();
		
		if (pt != null)
			return pt.getServices();
		
		return null;
	}
	
	public IService getService(String name) {
		IService s = this.registered.get(name);
		
		if (s != null)
			return s;
		
		ServiceResource parent = this.getParentResource();

		if (parent != null)
			return parent.getService(name);
		
		return null;
	}

	public boolean handle(ServiceRequest request) throws OperatingContextException {
		IService s = this.registered.get(request.getName());
		
		if ((s != null) && s.isEnabled()) {
			if (s.handle(request, request.getOutcome()))
				return true;
		}
		
		ServiceResource parent = this.getParentResource();

		if (parent != null)
			return parent.handle(request);
		
		Logger.errorTr(441, request.getName(), request.getFeature(), request.getOp());
		request.requireOutcome().returnEmpty();
		
		return false;
	}
	
	public boolean isServiceAvailable(String name) {
		if (this.registered.containsKey(name))
			return true;
		
		ServiceResource parent = this.getParentResource();

		if (parent != null)
			return parent.isServiceAvailable(name);
		
		return false;
	}
	
	public List<String> getTierServices() {
		return this.serviceorder;
	}
	
	public void registerTierService(String name, IService srv) {
		this.registered.put(name, srv);
		this.serviceorder.add(name);
		
		srv.start();
	}

	public void removeTierService(String name) {
		IService srv = this.registered.remove(name);
		this.serviceorder.remove(name);

		if (srv != null)
			srv.stop();
	}
	
	@Override
	public void cleanup() {
		List<String> services = new ArrayList<>(this.serviceorder);
		
		Collections.reverse(services);	// so we stop in reverse order
		
		for (String srv : services)
			this.removeTierService(srv);
	}
}
