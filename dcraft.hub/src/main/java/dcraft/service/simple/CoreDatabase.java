package dcraft.service.simple;

import dcraft.hub.ResourceHub;
import dcraft.hub.resource.ConfigResource;
import dcraft.tenant.Tenant;
import dcraft.tenant.TenantHub;
import dcraft.xml.XElement;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 */
public class CoreDatabase {
	// keyed by tenant alias
	protected Map<String, TenantData> tenants = new HashMap<>();
	
	public TenantData getTenant(String alias) {
		return this.tenants.get(alias);
	}
	
	public void init() {
		this.tenants.clear();
		
		// load the tenant user directory
		for (Tenant tenant : TenantHub.getTenants()) {
			String alias = tenant.getAlias();
			
			this.tenants.put(alias, TenantData.of(tenant));
		}
		
		/* TODO listen to hub reload
		// when tenant settings load update the user list
		ApplicationHub.subscribeToEvents(new IEventSubscriber() {
			@Override
			public void eventFired(Integer event, Object e) {
				// TODO restore
				//if (event != HubEvents.TenantLoaded)
				//	return;
				
				String alias = (String) e;
				
				Tenant du = TenantsService.this.tenantusers.get(alias);
				
				if (du == null)
					return;
				
				TenantInfo tinfo = TenantHub.resolveTenant(alias);
				
				// remove old user list
				du.clearCache();
				
				// load users from hub config file for tenant
				XElement tenants = ApplicationHub.getConfig().selectFirst("Tenants");
				
				if (tenants != null) {
					for (XElement mtenant : tenants.selectAll("Tenant")) {
						String talias = mtenant.getAttribute("Alias");

						if (! alias.equals(talias))
							continue;

						du.load(alias, mtenant);
						break;
					}
				}
				
				// load users from tenant config file
				XElement settings = tinfo.getSettings();
				
				if (settings != null)
					du.load(alias, settings.find("Users"));
			}
		});
		*/
	}
}
