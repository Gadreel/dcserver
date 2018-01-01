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

import java.util.Scanner;

import dcraft.hub.config.LocalConfigLoader;
import dcraft.hub.config.LocalHubConfigLoader;
import org.apache.commons.daemon.DaemonContext;
import org.apache.commons.daemon.DaemonInitException;

import dcraft.hub.app.ApplicationHub;
import dcraft.log.Logger;

public class Daemon implements org.apache.commons.daemon.Daemon {
	protected DaemonContext procCtx = null;

	public static void main(String[] args) {
		try {
			Daemon.startService(args);
			
			try (Scanner scan = new Scanner(System.in)) {
				System.out.println("Press enter to end Daemon");
				scan.nextLine();
			}
		}
		catch (Exception x) {
			
		}
		
		Daemon.stopService(args);
	}
	
	public static void startService(String[] args) {
		String deployment = (args.length > 0) ? args[0] : null;
		String nodeid = (args.length > 1) ? args[1] : null;
		
		ApplicationHub.init(deployment, nodeid);
		
		if (! ApplicationHub.startServer(LocalHubConfigLoader.local())) {
			Logger.error("Unable to continue, hub not properly started, please see logs");
			ApplicationHub.stopServer();
			System.exit(1);
			return;
		}
		
		Logger.info("Daemon started");
    }
	
	public static void stopService(String[] args) {
		Logger.info("Daemon stopping");
		
		ApplicationHub.stopServer();
	}
	
	@Override
	public void start() throws Exception {
		Daemon.startService(this.procCtx.getArguments());
	}

	@Override
	public void stop() throws Exception {
		Daemon.stopService(this.procCtx.getArguments());
	}

	@Override
	public void destroy() {
		this.procCtx = null;
	}

	@Override
	public void init(DaemonContext ctx) throws DaemonInitException, Exception {
		this.procCtx = ctx;
	}
}
