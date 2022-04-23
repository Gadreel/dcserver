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

/**
 * Support for testing the dcFileSever demo.  This shows the DivConq remote API
 * system support. 
 */
package dcraft.cli;

import dcraft.api.ApiSession;
import dcraft.hub.ILocalCommandLine;
import dcraft.hub.ResourceHub;
import dcraft.hub.ignite.IInitializeDeploymentCli;
import dcraft.hub.ignite.IServerHelper;
import dcraft.task.run.WorkHub;
import dcraft.util.*;
import dcraft.xml.XElement;
import dcraft.xml.XmlReader;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

public class Ignite implements ILocalCommandLine {
	@Override
	public void run(Scanner scan, ApiSession client) throws Exception {
		boolean running = true;

		while (running) {
			try {
				System.out.println();
				System.out.println("-----------------------------------------------");
				System.out.println("   dcIGNITE main menu");
				System.out.println("-----------------------------------------------");
				System.out.println("0)   Exit");
				System.out.println("1)   Add Deployment");
				System.out.println("20)  Hub Utilities");

				String opt = scan.nextLine();

				Long mopt = StringUtil.parseInt(opt);

				if (mopt == null)
					continue;

				running = this.runCommand(scan, client, mopt.intValue());
			}
			catch (Exception x) {
				System.out.println("Command Line Error: " + x);
			}
		}

		System.out.println();
		System.out.println("Waiting on tasks!");

		int waitcnt = 0;

		while ((WorkHub.queued() > 0) || (WorkHub.inprogress() > 0)) {
			try {
				Thread.sleep(1000);
			}
			catch (InterruptedException x) {

			}

			System.out.print(".");

			waitcnt++;

			if (waitcnt > 1200)
				break;
		}
	}

	protected boolean runCommand(Scanner scan, ApiSession client, int cmd) throws Exception {
		switch (cmd) {
			case 0: {
				return false;
			}

			case 1: {
				this.addDeployment(scan, client);
				break;
			}

			case 20: {
				new HubUtil().run(scan, client);
				break;
			}

		}

		return true;
	}

	protected void addDeployment(Scanner scan, ApiSession client) throws Exception {
		System.out.println("-----------------------------------------------");
		System.out.println("   Deployment Initialization");
		System.out.println("-----------------------------------------------");
		System.out.println();
		System.out.println("You will need a matrix.xml before you can add a deployment.");
		System.out.println("In the matrix you need to add the Deployment entry for the new deployment.");
		System.out.println("Also in matrix you will want to add DeveloperNodes and DeploymentTemplates.");

		// TODO link to documentation on website

		System.out.println();
		System.out.println("Are you sure you want to continue (y/n)?");

		String opt = scan.nextLine().toLowerCase();

		if (! opt.startsWith("y"))
			return;
		
		IServerHelper ssh = (IServerHelper) ResourceHub.getResources().getClassLoader().getInstance("dcraft.tool.release.ServerHelper");

		if (! ssh.init()) {
			System.out.println("Missing or incomplete matrix config - a matrix file is required.");
			return;
		}

		List<XElement> newdeployments = new ArrayList<>();

		for (XElement deploy : ssh.getMatrix().selectAll("Deployment")) {
			if (! deploy.hasNotEmptyAttribute("Alias"))
				continue;

			String alias = deploy.attr("Alias");

			Path cpath = Paths.get("./deploy-"  + alias + "/config/config.xml");

			if (Files.notExists(cpath)) {
				newdeployments.add(deploy);
			}
		}

		if (newdeployments.size() == 0) {
			System.out.println("All deployments listed in matrix are already configured.");
			return;
		}

		System.out.println("Choose a deployment to initialize:");
		System.out.println();
		for (int i = 0; i < newdeployments.size(); i++) {
			XElement deploy = newdeployments.get(i);

			System.out.println((i + 1) + ") " + deploy.attr("Title") + " - " + deploy.attr("Alias"));
		}

		System.out.println();
		System.out.println("Enter the number: ");
		String num = scan.nextLine();

		Long mopt = StringUtil.parseInt(num);

		if ((mopt == null) || (mopt < 1) || (mopt > newdeployments.size()))
			return;

		XElement selected = newdeployments.get(mopt.intValue() - 1);

		System.out.println();
		System.out.println("Initializing " + selected.attr("Title"));
		
		/*
		System.out.println();
		System.out.println("Select a template: ");
		System.out.println();

		List<Path> templates = new ArrayList<>();

		for (XElement templatesource : ssh.getMatrix().selectAll("DeploymentTemplates")) {
			String name = templatesource.attr("Name");

			if (StringUtil.isEmpty(name))
				continue;

			Path packagepath = Paths.get("./packages/" + name + "/deploy-templates");

			if (Files.exists(packagepath)) {
				try (DirectoryStream ds = Files.newDirectoryStream(packagepath)) {
					Iterator<Path> pathIterator = ds.iterator();

					if (pathIterator.hasNext()) {
						Path entry = pathIterator.next();

						while (entry != null) {
							if (Files.isDirectory(entry)) {
								templates.add(entry);
								System.out.println(templates.size() + ") " + name + " - " + entry.getFileName());
							}

							entry = pathIterator.hasNext() ? pathIterator.next() : null;
						}
					}
				}
			}
		}

		System.out.println();
		System.out.println("Enter the number: ");
		num = scan.nextLine();

		mopt = StringUtil.parseInt(num);

		if ((mopt == null) || (mopt < 1) || (mopt > templates.size()))
			return;

		Path template = templates.get(mopt.intValue() - 1);

		Path template = templates.get(mopt.intValue() - 1);
		*/
		
		String tname = selected.attr("Template");

		System.out.println();
		System.out.println("Using template " + tname);
		
		Path template = Paths.get("./templates/" + tname);
		
		XElement settings = XmlReader.loadFile(template.resolve("template.xml"), false, true);
		
		if (settings == null) {
			System.out.println("Missing template settings");
			return;
		}
		
		IInitializeDeploymentCli cli = (IInitializeDeploymentCli) ResourceHub.getResources().getClassLoader().getInstance(settings.attr("InitClass"));
		
		if (cli == null) {
			System.out.println("Missing template cli");
			return;
		}

		cli.run(scan, template, settings, ssh, selected, client);

		System.out.println("Deployment initialization exiting.");
	}
}
