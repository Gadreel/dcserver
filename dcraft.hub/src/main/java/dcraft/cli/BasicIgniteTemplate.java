package dcraft.cli;

import dcraft.api.ApiSession;
import dcraft.custom.work.DocBuilderWork;
import dcraft.hub.ignite.IInitializeDeploymentCli;
import dcraft.hub.ignite.IServerHelper;
import dcraft.hub.op.OperatingContextException;
import dcraft.struct.RecordStruct;
import dcraft.task.*;
import dcraft.util.FileUtil;
import dcraft.util.IOUtil;
import dcraft.util.StandardSettingsObfuscator;
import dcraft.util.StringUtil;
import dcraft.xml.XElement;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Scanner;

public class BasicIgniteTemplate implements IInitializeDeploymentCli {
	@Override
	public void run(Scanner scan, Path template, XElement settings, IServerHelper server, XElement deployment, ApiSession client) throws OperatingContextException {
		StandardSettingsObfuscator obfus = StandardSettingsObfuscator.obfus();
		XElement clock = obfus.configure(null, null);		// do this before encrypt below
		
		obfus = StandardSettingsObfuscator.obfus(clock);		// enable the proper crypto keys

		System.out.println("Enter deployment time zone in full tz database format (e.g. America/Chicago):");
		String chrono = scan.nextLine();

		if (StringUtil.isEmpty(chrono))
			chrono = "America/Chicago";

		String kpassword = StringUtil.buildSecurityCode(20);

		System.out.println("This will be your keyring password, please keep with your files: " + kpassword);
		System.out.println("Typically you do not need this password, but for recovery it could be useful.");

		XElement config = XElement.tag("Config")
				.attr("Chronology", chrono)
				.with(
						clock,
						XElement.tag("Profiles")
								.with(
										XElement.tag("Profile")
												.attr("TODO", "xxx")
								),
						XElement.tag("Keyrings")
							.attr("Password", obfus.encryptStringToHex(kpassword))
				);

		Path dpath = Paths.get("./deploy-"  + deployment.attr("Alias"));

		IOUtil.saveEntireFile(dpath.resolve("config/config.xml"), config.toPrettyString());

		HubUtil.initDeployKeys(dpath, deployment.attr("Alias"), kpassword);

		System.out.println("Common keys built");

		IOUtil.saveEntireFile(dpath.resolve("roles/server/config/config.xml"), XElement.tag("Config").attr("Title", "Web Server").toPrettyString());

		for (XElement node : deployment.selectAll("Node")) {
			HubUtil.initDeployNodeKeys(dpath, deployment.attr("Alias"), node.attr("Id"), kpassword);
		}
		
		for (XElement node : server.getMatrix().selectAll("DeveloperNodes/Node")) {
			HubUtil.initDeployNodeKeys(dpath, deployment.attr("Alias"), node.attr("Id"), kpassword);
		}

		System.out.println("Nodes initialized");

		XElement sharedconfig = XElement.tag("Config").attr("Title", "Root Tenant");

		IOUtil.saveEntireFile(dpath.resolve("tenants/root/config/shared.xml"), sharedconfig.toPrettyString());

		HubUtil.initDeployTenantKeys(dpath, deployment.attr("Alias"),"root", kpassword);

		XElement siteconfig = XElement.tag("Config").attr("Title", "Root Site");

		IOUtil.saveEntireFile(dpath.resolve("tenants/root/config/config.xml"), siteconfig.toPrettyString());
	}
}
