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
package dcraft.hub;

import java.io.BufferedReader;
import java.io.Console;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Scanner;

import dcraft.api.ApiSession;
import dcraft.api.LocalSession;
import dcraft.db.BasicRequestContext;
import dcraft.db.DatabaseAdapter;
import dcraft.db.DatabaseException;
import dcraft.filevault.work.IndexAllFilesWork;
import dcraft.filevault.work.IndexSiteFilesWork;
import dcraft.hub.app.ApplicationHub;
import dcraft.hub.clock.SysReporter;
import dcraft.hub.config.LocalHubConfigLoader;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
import dcraft.hub.op.UserContext;
import dcraft.hub.resource.ConfigResource;
import dcraft.log.DebugLevel;
import dcraft.log.HubLog;
import dcraft.struct.RecordStruct;
import dcraft.task.*;
import dcraft.task.run.WorkHub;
import dcraft.task.run.WorkTopic;
import dcraft.tool.release.ServerHelper;
import dcraft.tool.release.SshHelper;
import dcraft.util.IOUtil;
import dcraft.util.StringUtil;
import dcraft.xml.XElement;

/*
 */
public class Foreground {
	static public TaskContext lastdebugrequest = null; 
	
	public static void main(String[] args) {
		String deployment = (args.length > 0) ? args[0] : null;
		String nodeid = (args.length > 1) ? args[1] : ApplicationHub.getNodeId();
		
		ApplicationHub.init(deployment, nodeid);
		
		if (args.length > 2)
			ApplicationHub.setRole(args[2]);
		
		System.out.println("dcServer starting");
		
		if (ApplicationHub.startServer(LocalHubConfigLoader.local())) {
			// for foreground work
			OperationContext.set(OperationContext.context(UserContext.rootUser()));
			
			Scanner scan = new Scanner(System.in, "UTF-8");
			
			ConfigResource configres = ResourceHub.getResources().getConfig();
			
			XElement cliel = configres.getTag("CommandLine");
			
			ILocalCommandLine cli = (ILocalCommandLine) ResourceHub.getResources().getClassLoader().getInstance(cliel.getAttribute("ClientClass", "dcraft.cli.HubUtil"));
			
			ApiSession capi = null;
			boolean auth = false;

				/*
			boolean auth = true;

			while (true) {
				System.out.print("Tenant (e.g. root): ");
				String domain = scan.nextLine();
				
				if ("-".equals(domain)) {
					System.out.println("--------------------------------------------");
					continue;
				}
				
				if ("0".equals(domain)) {
					auth = false;
					break;
				}
				
				if ("*".equals(domain)) {
					capi = LocalSession.local("root");
					
					if (capi == null) {
						System.out.println("Domain not found.");
						continue;
					}
					
					if (capi.signin("root","A1s2d3f4"))
						break;
				}
				else {
					capi = LocalSession.local(domain);
					
					if (capi == null) {
						System.out.println("Domain not found.");
						continue;
					}
					
					System.out.print("Username: ");
					String user = scan.nextLine();
					
					Console cons = null;
					String pass = null;
					char[] passwd = null;
					
					if ((cons = System.console()) != null && (passwd = cons.readPassword("Password:")) != null) {
						pass = new String(passwd);
					}
					else {
						System.out.print("Password: ");
						pass = scan.nextLine();
					}
					
					if (capi.signin(user, pass))
						break;
				}
				
				System.out.println("Failed");
			}
			*/

			String domain = "root";

			capi = LocalSession.local(domain);

			if (capi == null) {
				System.out.println("Root domain not found.");
			}
			else {
				String user = "root";

				Console cons = System.console();

				while (! auth) {
					String pass = null;

					if (cons != null) {
						char[] passwd = cons.readPassword("Password:");

						if (passwd != null)
							pass = new String(passwd);
					}
					else {
						System.out.print("Password: ");
						pass = scan.nextLine();
					}

					if ("-".equals(pass)) {
						System.out.println("--------------------------------------------");
						continue;
					}

					if ("0".equals(pass))
						break;

					if ("*".equals(pass))
						pass= "A1s2d3f4";

					if ("KillAllBlockedIP".equals(pass)) {
						try {
							BasicRequestContext requestContext = BasicRequestContext.ofDefaultDatabase();
							DatabaseAdapter conn = requestContext.getInterface();

							conn.kill("root", "dcIPTrust");

							// don't yet have permission to this function
							//capi.callWait(ServiceRequest.of("dcmServices.Dashboard.KillAllBlockedIP"), TimeoutPlan.Regular);
						}
						catch (OperatingContextException x) {
							System.out.println("issue operating: " + x);
						}
						catch (DatabaseException x) {
							System.out.println("issue clearing: " + x);
						}
					}

					if (pass != null)
						auth = capi.signin(user, pass);
				}

				if (auth) {
					try {
						if (capi != null)
							cli.run(scan, capi);
					}
					catch(Exception x) {
						System.out.println("Unable to start commandline interface");
					}
				}

				capi.stop();
			}
		}
		else {
			System.out.println("dcServer failed to start");
		}
		
		ApplicationHub.stopServer();
	}
		
	static public void utilityMenu(Scanner scan) { 	
		boolean running = true;
		
		while(running) {
			try {
				System.out.println();
				System.out.println("-----------------------------------------------");
				System.out.println("   Node " + ApplicationHub.getNodeId() + " Utility Menu");
				System.out.println("-----------------------------------------------");
				System.out.println("0)  Exit");
				System.out.println("1)  Encrypt Setting");
				System.out.println("2)  Hash Setting");
				System.out.println("3)  Hash Password Setting");
				System.out.println("4)  System Status");
				System.out.println("5)  Backup Server");
				System.out.println("6)  File ReIndex Vaults");
				System.out.println("7)  File ReIndex Site Vaults");
				System.out.println("10)  Production Server Set Up");
				System.out.println("11)  Production Bundle dcServer");
				System.out.println("100)  Enter Script Debugger");

				String opt = scan.nextLine();
				
				Long mopt = StringUtil.parseInt(opt);
				
				if (mopt == null)
					continue;
				
				switch (mopt.intValue()) {
				case 0:
					running = false;
					break;
				case 1: {
					System.out.println("Enter setting to encrypt:");
					String val = scan.nextLine();
					
					System.out.println("Result: "+ ApplicationHub.getClock().getObfuscator().encryptStringToHex(val));
					break;
				}
				case 2: {
					System.out.println("Enter setting to hash:");
					String val = scan.nextLine();
					
					System.out.println("Result: "+ ApplicationHub.getClock().getObfuscator().hashStringToHex(val));
					break;
				}
				case 3: {
					System.out.println("Enter password to hash:");
					String val = scan.nextLine();
					
					System.out.println("Result: "+ ApplicationHub.getClock().getObfuscator().hashPassword(val));
					break;
				}
				case 793: {
					System.out.println("Enter setting to decrypt:");
					String val = scan.nextLine();
					
					System.out.println("Result: "+ ApplicationHub.getClock().getObfuscator().decryptHexToString(val));
					break;
				}
				case 4: {
					Foreground.dumpStatus();
					break;
				}
				case 6: {
					Task task = Task.ofSubtask("ReIndex Vault Files", "Vault")
							.withTimeout(10)
							.withWork(new IndexAllFilesWork());
					
					TaskHub.submit(task, new TaskObserver() {
						@Override
						public void callback(TaskContext subtask) {
							if (subtask.hasExitErrors())
								System.out.println("Failed to index the files.");
							else
								System.out.println("Files Indexed.");
						}
					});
					
					break;
				}
				case 7: {
					System.out.println("Tenant:");
					String tenant = scan.nextLine();
					
					System.out.println("Site:");
					String site = scan.nextLine();
					
					OperationContext tctx = OperationContext.context(
							UserContext.rootUser(tenant, site));
					
					Task task = Task.ofContext(tctx)
							.withTitle("File Index a site")
							.withTimeout(10)
							.withWork(new IndexSiteFilesWork());
					
					TaskHub.submit(task, new TaskObserver() {
						@Override
						public void callback(TaskContext subtask) {
							if (subtask.hasExitErrors())
								System.out.println("Failed to index the files.");
							else
								System.out.println("Files Indexed.");
						}
					});
					
					break;
				}

				case 10: {
					System.out.println("Which deployment to check? ");
					String deploy = scan.nextLine();

					if (StringUtil.isEmpty(deploy))
						break;

					System.out.println();

					System.out.println("Disk name (enter to default to 'nvme1n1'): ");
					String diskname = scan.nextLine();

					if (StringUtil.isEmpty(diskname))
						diskname = "nvme1n1";

					ServerHelper ssh = new ServerHelper();

					if (! ssh.init(deploy)) {
						System.out.println("Missing or incomplete matrix config");
						break;
					}

					SshHelper ph = SshHelper.of(ssh);

					if (ph == null) {
						System.out.println("Bad publisher");
						break;
					}

					try {
						ph.connect();

						//ph.getShell().getInputStream();

						//String out = ph.exec("ls -la");

						//System.out.println("Got: " + out);

						/* TODO restore these lines

						## Basic Setup: Elastic IP, Security Group, Key Pairs

						## Add Server, disks
						*/

						if ("Update".equals(ph.checkLinuxUpdateStatus())) {
							ph.runLinuxUpdate();
						}

						ph.prepDisk(diskname, "/dcserver");

						ph.checkInstallPackage("htop");

						ph.checkInstallPackage("java-11-amazon-corretto-headless");

						ph.setWebPorts("8080", "8443");

						XElement nodesettings = ssh.findDeployment();

						// TODO enhance for multiple nodes
						ph.setWebServerVars(deploy, nodesettings.attr("Id"));

						// TODO setup dcServer

						HubLog.setGlobalLevel(DebugLevel.Trace);
					}
					finally {
						ph.disconnect();

						HubLog.setGlobalLevel(DebugLevel.Info);
					}

					break;
				}

				case 11: {
					System.out.println("Which deployment to bundle? ");
					String deploy = scan.nextLine();

					if (StringUtil.isEmpty(deploy))
						break;

					System.out.println("Which node to bundle? ");
					String node = scan.nextLine();

					if (StringUtil.isEmpty(node))
						break;

					StringBuilder sb = new StringBuilder();

					sb.append("./lib");
					sb.append('\n');
					sb.append("./util");
					sb.append('\n');
					sb.append("./packages");
					sb.append('\n');
					sb.append("./deploy-" + deploy + "/config");
					sb.append('\n');
					sb.append("./deploy-" + deploy + "/roles");
					sb.append('\n');
					sb.append("./deploy-" + deploy + "/tenants");
					sb.append('\n');
					sb.append("./deploy-" + deploy + "/nodes/" + node);
					sb.append('\n');
					sb.append("./foreground.sh");
					sb.append('\n');
					sb.append("./LICENSE.txt");
					sb.append('\n');
					sb.append("./server.sh");
					sb.append('\n');

					IOUtil.saveEntireFile(Path.of("./temp/filelist.txt"), sb.toString());

					{
						RecordStruct shellParams = ShellWork.buildTaskParams("./util/bundle.sh", ".", 20000L);
						ProcessBuilder pb = ShellWork.paramsToProcessBuilder(shellParams);

						Process proc = pb.start();

						try (BufferedReader reader = new BufferedReader(new InputStreamReader(proc.getInputStream()))) {
							String line = null;
							StringBuilder sb2 = new StringBuilder();

							while ((line = reader.readLine()) != null) {
								sb2.append(line);
								sb2.append('\n');
							}

							System.out.println("out: " + sb2);
						}
						catch (IOException x) {
							System.out.println("error: " + x);
						}
					}

					break;
				}
				case 100: {
					System.out.println("Under construction");;
					//Foreground.debugScript(scan);
					break;
				}
				}
			}
			catch(Exception x) {
				System.out.println("CLI error: " + x);
			}
		}		
	}
	
	/*
	static void debugScript(Scanner scn) {
		TaskContext r = Foreground.lastdebugrequest;
		
		if (r == null) {
			System.out.println("No debugger requests are availabled.");
			return;
		}
		
		Activity act = (Activity) Foreground.lastdebugrequest.getTask().getWork();
		act.setInDebugger(true);
		
		AtomicLong lastinstrun = new AtomicLong(act.getRunCount());
		AtomicLong lastinstmrk = new AtomicLong(lastinstrun.get());
		
		ApplicationHub.getClock().schedulePeriodicInternal(new Runnable() {
			@Override
			public void run() {
				long cnt = act.getRunCount();
				
				if (lastinstrun.get() == cnt)
					return;
				
				lastinstrun.set(cnt);
				
				if (r.isComplete())
					System.out.println("DEBUGGER: Press enter to exit or ? for help.");
				else {
					RecordStruct debuginfo = act.getDebugInfo();
					
					ListStruct stack = debuginfo.getFieldAsList("Stack");					
					RecordStruct currinst = stack.getItemAsRecord(stack.size() - 1);
					long line = currinst.getFieldAsInteger("Line");
					long col = currinst.getFieldAsInteger("Column");
					
					System.out.println("DEBUGGER: (" + line + "," + col +") " + currinst.getFieldAsString("Command"));
					System.out.println("DEBUGGER: Press enter to continue or ? for help.");
				}
			}
		}, 1);
		
		System.out.println("DEBUGGER: Press enter to continue or ? for help.");
		
		while (!r.isComplete()) {
			String cmd = scn.nextLine();
			
			// dump
			if (cmd.startsWith("d")) {
				System.out.println("------------------------------------------------------------------------");

				try {
					act.getDebugInfo().toBuilder(new JsonStreamBuilder(System.out, true));	
				}
				catch (Exception x) {
					System.out.println("DEBUGGER: unable to dump" + x);
				}
				
				System.out.println("------------------------------------------------------------------------");
			}
			// help
			else if (cmd.startsWith("?")) {
				System.out.println("(n)ext");
				System.out.println("(r)un");
				System.out.println("(s)top");
				System.out.println("(d)ump stack");
			}
			// stop
			else if (cmd.startsWith("s")) {
				r.kill();
			}
			// run
			else if (cmd.startsWith("r")) {
				if (lastinstmrk.get() > lastinstrun.get()) {
					System.out.println("DEBUGGER: Wait, scriptold is executing...");
				}
				else {
					lastinstmrk.set(lastinstrun.get());
					lastinstmrk.incrementAndGet();
					
					System.out.println("DEBUGGER: Running...");
					act.setDebugMode(false);
					TaskHub.submit(r);
				}
			}
			// next
			else if (!r.isComplete()) {
				if (lastinstmrk.get() > lastinstrun.get()) {
					System.out.println("DEBUGGER: Wait, scriptold is executing...");
				}
				else {
					lastinstmrk.set(lastinstrun.get());
					lastinstmrk.incrementAndGet();
					
					System.out.println("DEBUGGER: Executing...");
					act.setDebugMode(true);
					TaskHub.submit(r);
				}
			}
		}
		
		System.out.println("DEBUGGER: Script done.");
	}
	*/
	
	static public void dumpStatus() {
		System.out.println(" ------------------------------------------- ");
		//System.out.println("        Pool: " + pool.getName());
		//System.out.println("    Back Log: " + pool.backlog());
		
		//System.out.println("  Busy Level: " + pool.howBusy());
		
		System.out.println("   # Threads: " + WorkHub.threadCount());
		System.out.println("   # Created: " + WorkHub.threadsCreated());
		System.out.println("      # Hung: " + WorkHub.threadsHung());
		System.out.println(" ------------------------------------------- ");
		
		for (WorkTopic topic : WorkHub.getTopics()) {
			System.out.println(" Topic:        " + topic.getName());
			System.out.println(" - In Progress: " + topic.inprogress());
			
			for (TaskContext task : topic.tasksInProgress()) {
				System.out.println(" -- " + task.getTask().getId());
			}
		}
		
		SysReporter rep = ApplicationHub.getClock().getSlowSysReporter();
		
		System.out.println(" Slow Sys Work Status: " + rep.getStatus() + " @ " + ZonedDateTime.ofInstant(Instant.ofEpochMilli(rep.getLast()), ZoneId.of("UTC")));
		
		rep = ApplicationHub.getClock().getFastSysReporter();
		
		System.out.println(" Fast Sys Work Status: " + rep.getStatus() + " @ " + ZonedDateTime.ofInstant(Instant.ofEpochMilli(rep.getLast()), ZoneId.of("UTC")));
		
		// TODO ServiceHub.dumpInfo();
		
		// TODO Scheduler.dump();
	}
}
