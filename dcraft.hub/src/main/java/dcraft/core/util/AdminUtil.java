package dcraft.core.util;

import dcraft.filestore.CommonPath;
import dcraft.filevault.Vault;
import dcraft.hub.op.OperatingContextException;
import dcraft.tenant.Site;
import dcraft.tenant.Tenant;

import java.util.*;

public class AdminUtil {
	static public SiteVaultPath SiteVaultPath(Tenant t, Site s, CommonPath path) throws OperatingContextException {
		String firstName = null;

		if (path.getNameCount() > 0)
			firstName = path.getName(0);

		Vault vault = s.getVault("SiteFiles");

		if ("galleries".equals(firstName)) {
			vault = s.getVault("Galleries");
			path = path.subpath(1);
		}
		else if ("files".equals(firstName)) {
			vault = s.getVault("Files");
			path = path.subpath(1);
		}
		else if ("feeds".equals(firstName)) {
			vault = s.getVault("Feeds");
			path = path.subpath(1);
		}
		else if ("meta".equals(firstName)) {
			vault = s.getVault("Meta");
			path = path.subpath(1);
		}
		else if ("communicate".equals(firstName)) {
			vault = s.getVault("Communicate");
			path = path.subpath(1);
		}
		else if ("vault".equals(firstName)) {
			if (path.getNameCount() > 1) {
				String secondName = path.getName(1);

				vault = s.getVault(secondName);
				path = path.subpath(2);
			}
			else {
				return null;
			}
		}

		SiteVaultPath result = new SiteVaultPath();
		result.vault = vault;
		result.path = path;

		return result;
	}

	static public class SiteVaultPath {
		public Vault vault = null;
		public CommonPath path = null;
	}

	static public class VaultUpdatePlan {
		static VaultUpdatePlan of(Tenant t, Site s, Vault v) {
			VaultUpdatePlan plan = new VaultUpdatePlan();
			plan.tenant = t;
			plan.site = s;
			plan.vault = v;
			return plan;
		}

		public Tenant tenant = null;
		public Site site = null;
		public Vault vault = null;
		public List<CommonPath> updates = new ArrayList<>();
		public List<CommonPath> deletes = new ArrayList<>();
	}

	static public class VaultPlanOrganizer {
		protected HashMap<String, VaultPlanTenant> tenantHashMap = new HashMap<>();

		public void update(Tenant t, Site s, SiteVaultPath vaultPath) {
			if (! this.tenantHashMap.containsKey(t.getAlias()))
				this.tenantHashMap.put(t.getAlias(), new VaultPlanTenant());

			this.tenantHashMap.get(t.getAlias()).update(t, s, vaultPath);
		}

		public void delete(Tenant t, Site s, SiteVaultPath vaultPath) {
			if (! this.tenantHashMap.containsKey(t.getAlias()))
				this.tenantHashMap.put(t.getAlias(), new VaultPlanTenant());

			this.tenantHashMap.get(t.getAlias()).delete(t, s, vaultPath);
		}

		public void collect(Collection<VaultUpdatePlan> plans) {
			for (Map.Entry<String, VaultPlanTenant> set : this.tenantHashMap.entrySet()) {
				set.getValue().collect(plans);
			}
		}
	}

	static public class VaultPlanTenant {
		protected HashMap<String, VaultPlanSite> siteHashMap = new HashMap<>();

		public void update(Tenant t, Site s, SiteVaultPath vaultPath) {
			if (! this.siteHashMap.containsKey(s.getAlias()))
				this.siteHashMap.put(s.getAlias(), new VaultPlanSite());

			this.siteHashMap.get(s.getAlias()).update(t, s, vaultPath);
		}

		public void delete(Tenant t, Site s, SiteVaultPath vaultPath) {
			if (! this.siteHashMap.containsKey(s.getAlias()))
				this.siteHashMap.put(s.getAlias(), new VaultPlanSite());

			this.siteHashMap.get(s.getAlias()).delete(t, s, vaultPath);
		}

		public void collect(Collection<VaultUpdatePlan> plans) {
			for (Map.Entry<String, VaultPlanSite> set : this.siteHashMap.entrySet()) {
				set.getValue().collect(plans);
			}
		}
	}

	static public class VaultPlanSite {
		protected HashMap<String, VaultUpdatePlan> vaultHashMap = new HashMap<>();

		public void update(Tenant t, Site s, SiteVaultPath vaultPath) {
			if (! this.vaultHashMap.containsKey(vaultPath.vault.getName()))
				this.vaultHashMap.put(vaultPath.vault.getName(), VaultUpdatePlan.of(t, s, vaultPath.vault));

			this.vaultHashMap.get(vaultPath.vault.getName()).updates.add(vaultPath.path);
		}

		public void delete(Tenant t, Site s, SiteVaultPath vaultPath) {
			if (! this.vaultHashMap.containsKey(vaultPath.vault.getName()))
				this.vaultHashMap.put(vaultPath.vault.getName(), VaultUpdatePlan.of(t, s, vaultPath.vault));

			this.vaultHashMap.get(vaultPath.vault.getName()).deletes.add(vaultPath.path);
		}

		public void collect(Collection<VaultUpdatePlan> plans) {
			for (Map.Entry<String, VaultUpdatePlan> set : this.vaultHashMap.entrySet()) {
				plans.add(set.getValue());
			}
		}
	}
}
