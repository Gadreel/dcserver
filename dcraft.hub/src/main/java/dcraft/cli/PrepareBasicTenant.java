package dcraft.cli;

import dcraft.api.ApiSession;
import dcraft.db.request.common.RequestFactory;
import dcraft.filestore.local.LocalStore;
import dcraft.hub.ILocalCommandLine;
import dcraft.hub.ResourceHub;
import dcraft.hub.app.ApplicationHub;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OutcomeDump;
import dcraft.struct.RecordStruct;
import dcraft.task.*;
import dcraft.tenant.TenantHub;
import dcraft.util.FileUtil;
import dcraft.util.IOUtil;
import dcraft.util.StringUtil;
import dcraft.xml.XElement;
import dcraft.xml.XmlReader;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Scanner;

public class PrepareBasicTenant implements ILocalCommandLine {
	@Override
	public void run(Scanner scan, ApiSession client) throws Exception {
		char[] kpassword = ResourceHub.getResources().getKeyRing().getPassphrase();

		System.out.println("Tenant Alias:");
		String tenant = scan.nextLine();
		
		if (StringUtil.isEmpty(tenant))
			return;

		LocalStore fs = TenantHub.getFileStore();
		Path dpath = fs.resolvePath(tenant);
		
		if (! Files.exists(dpath.resolve("config/shared.xml"))) {
			System.out.println("Tenant Name:");
			String tenantName = scan.nextLine();

			if (StringUtil.isEmpty(tenantName))
				return;

			System.out.println("Local Domain Name:");
			System.out.println("(domain used to run locally)");
			String domain = scan.nextLine();

			if (StringUtil.isEmpty(domain))
				return;

			XElement shared = XElement.tag("Config").attr("Title", tenantName);

			IOUtil.saveEntireFile(dpath.resolve("config/shared.xml"), shared.toPrettyString());

			XElement config = XElement.tag("Config").attr("Title", tenantName)
					.with(
							XElement.tag("Domain")
								.attr("Name", domain)
								.attr("Use", "Local")
								.attr("Certificate", "false"),
							XElement.tag("Packages").with(
									XElement.tag("Package").attr("Name", "dc/dcWeb")
							),
							XElement.tag("Web").attr("HtmlMode", "Dynamic")
					);

			IOUtil.saveEntireFile(dpath.resolve("config/config.xml"), config.toPrettyString());

			System.out.println();
			System.out.println("tenant-shared written");
			System.out.println("creating tenant keys - this can take some time, please wait");

			HubUtil.initDeployTenantKeys(ApplicationHub.getDeploymentPath(), ApplicationHub.getDeployment(), tenant, new String(kpassword));

			System.out.println("tenant keys built");
		}

		client.call(RequestFactory.addTenantRequest(tenant)
				.toServiceRequest()
				.withOutcome(OutcomeDump.dump("Add Tenant"))
		);

		System.out.println("tenant added to db, restart server to activate");
	}
}
