package dcraft.hub.op;

import dcraft.hub.ResourceHub;
import dcraft.hub.resource.ResourceTier;
import dcraft.session.Session;
import dcraft.session.SessionHub;
import dcraft.tenant.Site;
import dcraft.tenant.Tenant;
import dcraft.tenant.TenantHub;

public class OperationResourceTier extends ResourceTier {
	static public OperationResourceTier tier(String sessid, String tenantalias, String sitealias) {
		OperationResourceTier srt = new OperationResourceTier();
		srt.sess = sessid;
		srt.tenantalias = tenantalias;
		srt.sitealias = sitealias;
		return srt;
	}
	
	static public OperationResourceTier tier(String tenantalias, String sitealias) {
		OperationResourceTier srt = new OperationResourceTier();
		srt.tenantalias = tenantalias;
		srt.sitealias = sitealias;
		return srt;
	}
	
	protected String sess = null;
	protected String tenantalias = null;
	protected String sitealias = null;
	
	@Override
	public ResourceTier getParent() {
		if (this.sess != null) {
			Session s = SessionHub.lookup(this.sess);
			
			if (s != null)
				return s.getResources();
		}
		
		if (this.tenantalias == null)
			return ResourceHub.getTopResources();
		
		Tenant tenant = TenantHub.resolveTenant(this.tenantalias);
		
		if (tenant == null)
			return ResourceHub.getTopResources();
		
		if (this.sitealias == null)
			return tenant.getResources();
		
		Site site = tenant.resolveSite(this.sitealias);
		
		if (site == null)
			return tenant.getResources();
		
		return site.getResources();
	}
	
	protected OperationResourceTier() {
	}
}
