package dcraft.service.db;

import dcraft.hub.app.ApplicationHub;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationOutcomeStruct;
import dcraft.hub.resource.ConfigResource;
import dcraft.log.Logger;
import dcraft.service.BaseDataService;
import dcraft.service.base.Vaults;
import dcraft.service.ServiceRequest;
import dcraft.service.simple.Authentication;
import dcraft.service.simple.CoreDatabase;
import dcraft.service.simple.Tenants;
import dcraft.struct.ListStruct;
import dcraft.struct.Struct;
import dcraft.task.TaskContext;
import dcraft.task.TaskHub;
import dcraft.task.TaskObserver;
import dcraft.tenant.Tenant;
import dcraft.tenant.TenantHub;
import dcraft.tenant.work.TenantFactory;
import dcraft.util.StringUtil;
import dcraft.xml.XElement;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 */
public class Service extends BaseDataService {
	protected CoreDatabase db = new CoreDatabase();
	
	@Override
	public void start() {
		/*
		ConfigResource configres = this.tier.getConfig();
		
		XElement tenants = configres.getTag("Tenants");
		
		// create a root tenant if none listed
		if (tenants == null) {
			tenants = new XElement("Tenants")
					.with(new XElement("Tenant")
							.withAttribute("Alias", "root")
							.withAttribute("Title", "Server management")
					);
		}
		
		List<Tenant> list = new ArrayList<>();
		
		// load the tenant user directory
		for (XElement mtenant : tenants.selectAll("Tenant")) {
			String alias = mtenant.getAttribute("Alias");
			
			if (StringUtil.isNotEmpty(alias)) {
				Tenant t = Tenant.of(alias);
				t.getResourcesOrCreate(this.tier).getOrCreateTierConfig().add(mtenant);
				list.add(t);
			}
		}
		*/
		
		Path tpath = TenantHub.getFileStore().getPath();
		
		List<Tenant> list = new ArrayList<>();
		
		try (Stream<Path> ps = Files.list(tpath)) {
			ps.forEach(p -> {
				if (Files.isDirectory(p)) {
					String alias = p.getFileName().toString();
					
					// offline is a flag file telling us to not load this tenant
					if (! alias.startsWith("_") && Files.notExists(p.resolve("offline"))) {
						Tenant t = Tenant.of(alias);
						t.getResourcesOrCreate(this.tier).getOrCreateTierConfig();
						list.add(t);
					}
				}
			} );
		}
		catch (IOException x) {
			Logger.error("Unable to load tenants folder: " + x);
		}
		
		TaskHub.submit(TenantFactory.updateTenants(false, list, null, false), new TaskObserver() {
			@Override
			public void callback(TaskContext task) {
				db.init();
				Service.super.start();
			}
		});
	}
	
	@Override
	public boolean handle(ServiceRequest request, OperationOutcomeStruct callback) throws OperatingContextException {
		if ("Tenants".equals(request.getFeature()))
			if (Tenants.handle(request, callback, this.db))
				return true;
		
		if ("Authentication".equals(request.getFeature()))
			if (Authentication.handle(request, callback, this.db))
				return true;
		
		if ("Vaults".equals(request.getFeature()))
			if (Vaults.handle(request, callback))
				return true;
		
		if ("Management".equals(request.getFeature())) {
			System.out.println("a");

			if ("ReloadTenants".equals(request.getOp())) {
				System.out.println("b");

				ListStruct list = request.getDataAsList();

				if (list != null) {
					List<Tenant> tenants = new ArrayList<>();

					for (int i = 0; i < list.size(); i++) {
						String alias = list.getItemAsString(i);

						if (StringUtil.isNotEmpty(alias)) {
							Tenant tenant = TenantHub.resolveTenant(alias);

							if (tenant != null) {
								tenants.add(tenant);
							}
						}
					}

					TaskHub.submit(TenantFactory.updateTenants(true, tenants, null, true), new TaskObserver() {
						@Override
						public void callback(TaskContext task) {
							System.out.println("d");

							callback.returnEmpty();
						}
					});
				}

				System.out.println("c");

				return true;
			}

			System.out.println("x");

			if ("UpdateTenants".equals(request.getOp())) {
				this.handleUpdateTenants(request, callback);
				return true;
			}

			if ("UpdateCore".equals(request.getOp())) {
				this.handleUpdateCore(request, callback);
				return true;
			}
		}
		
		return super.handle(request, callback);
	}
	
	public void handleUpdateTenants(ServiceRequest request, OperationOutcomeStruct callback) {
		ConfigResource configres = this.tier.getConfig();
		
		XElement tenants = configres.getTag("Tenants");
		
		// create a root tenant if none listed
		if (tenants == null) {
			tenants = new XElement("Tenants")
					.with(new XElement("Tenant")
							.withAttribute("Alias", "root")
							.withAttribute("Title", "Server management")
					);
		}
		
		ListStruct updates = request.getDataAsList();
		
		List<Tenant> list = new ArrayList<>();
		
		// load the tenant user directory
		for (XElement mtenant : tenants.selectAll("Tenant")) {
			String alias = mtenant.getAttribute("Alias");
			
			if (StringUtil.isEmpty(alias))
				continue;
			
			for (Struct us : updates.items()) {
				if (alias.equals(us.toString())) {
					Tenant t = Tenant.of(alias);
					t.getResourcesOrCreate(this.tier).getOrCreateTierConfig().add(mtenant);
					list.add(t);
				}
			}
		}
		
		TaskHub.submit(TenantFactory.updateTenants(true, list, null, true), new TaskObserver() {
			@Override
			public void callback(TaskContext task) {
				db.init();
				callback.returnEmpty();
			}
		});
	}
	
	public void handleUpdateCore(ServiceRequest request, OperationOutcomeStruct callback) throws OperatingContextException {
		ApplicationHub.restartServer(callback);
	}
}
