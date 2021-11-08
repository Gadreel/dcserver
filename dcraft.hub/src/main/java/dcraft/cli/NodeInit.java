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
import dcraft.db.Constants;
import dcraft.db.DbServiceRequest;
import dcraft.db.request.DataRequest;
import dcraft.db.request.common.RequestFactory;
import dcraft.db.rocks.Adapter;
import dcraft.db.rocks.ConnectionManager;
import dcraft.db.rocks.keyquery.KeyQuery;
import dcraft.db.util.ByteUtil;
import dcraft.db.util.DbUtil;
import dcraft.hub.Foreground;
import dcraft.hub.ILocalCommandLine;
import dcraft.hub.ResourceHub;
import dcraft.hub.app.ApplicationHub;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationOutcomeStruct;
import dcraft.struct.BaseStruct;
import dcraft.struct.Struct;
import dcraft.util.HexUtil;
import dcraft.util.RndUtil;
import dcraft.util.StandardSettingsObfuscator;
import dcraft.util.StringUtil;
import dcraft.util.pgp.KeyRingCollection;
import dcraft.xml.XElement;
import org.bouncycastle.openpgp.PGPPublicKeyRing;
import org.rocksdb.BackupInfo;
import org.rocksdb.RocksIterator;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static dcraft.db.Constants.*;

public class NodeInit implements ILocalCommandLine {
	/*
	 * Consider developing CLI further with libraries like these:
	 * 
	 * https://github.com/jline/jline3
	 * https://github.com/fusesource/jansi
	 * 
	 */
	@Override
	public void run(Scanner scan, ApiSession api) {
		try {
			System.out.println("-----------------------------------------------");
			System.out.println("   Node Initialization");
			System.out.println("-----------------------------------------------");
			System.out.println();
			System.out.println("You could lose your node setup (database) by doing this command.");
			System.out.println("Are you sure you want to continue (y/n)?");

			String opt = scan.nextLine().toLowerCase();

			if (! opt.startsWith("y"))
				return;

			System.out.println("Initialize Root Tenant");
			System.out.println();

			System.out.print("Global Root Password (required): ");
			String password = scan.nextLine();

			if (StringUtil.isEmpty(password)) {
				System.out.println("required!");
				return;
			}

			System.out.print("Root User Email (required): ");
			String email = scan.nextLine();

			if (StringUtil.isEmpty(email)) {
				System.out.println("required!");
				return;
			}

			System.out.print("Node: ");
			String node = scan.nextLine();

			if (! ApplicationHub.isValidNodeId(node)) {
				System.out.println("invalid node id");
				return;
			}

			String deployment = ApplicationHub.getDeployment();

			System.out.println();

			// not the best place for this - use node management in Ignite - if used here then more keys are added on nodes- including in production
			
			//Path cpath = Paths.get("./deploy-" + deployment);
			//HubUtil.initDeployNodeKeys(cpath, deployment, node, new String(ResourceHub.getResources().getKeyRing().getPassphrase()));


			XElement clock1 = ResourceHub.getTopResources().getConfig().getTag("Clock");

			String obfclass = "dcraft.util.StandardSettingsObfuscator";

			String clockid = clock1.attr("Id");

			byte[] feedbuff = new byte[64];
			RndUtil.random.nextBytes(feedbuff);
			String obfseed = HexUtil.bufferToHex(feedbuff);

			String dbname = "default";

			Path npath = Paths.get("./deploy-" + deployment + "/nodes/" + node);

			ConnectionManager db = this.getDatabase(npath.resolve("database").resolve(dbname),
					npath.resolve("database-bak").resolve(dbname), true);

			if (db == null) {
				System.out.println("Database missing or bad!");
				return;
			}

			DataRequest request = RequestFactory.addTenantRequest("root")
					.withParam("Password", password)
					.withParam("Config", XElement.tag("Config")
							.with(XElement.tag("Clock")
									.withAttribute("TimerClass", obfclass)
									.withAttribute("Id", clockid)
									.withAttribute("Feed", obfseed)
							))
					.withParam("First", "Root")
					.withParam("Last", "User")
					.withParam("Email", email);

			CountDownLatch lock = new CountDownLatch(1);

			DbUtil.execute((DbServiceRequest) request.toDbServiceRequest()
					.withOutcome(new OperationOutcomeStruct() {
						@Override
						public void callback(BaseStruct result) throws OperatingContextException {
							System.out.println("Root added");

							db.stop();

							lock.countDown();
						}
					}), db);


			while (! lock.await(1, TimeUnit.SECONDS)) {
				System.out.print(".");
			}

			System.out.println("Database configured, root tenant added.");

		}
		catch (Exception x) {
			System.out.println("Command Line Error: " + x);
		}
	}

	public ConnectionManager getDatabase(Path dbpath, Path dbbakpath, boolean createIfNotPresent) {
		if (!Files.exists(dbpath)) {
			if (createIfNotPresent)
				try {
					Files.createDirectories(dbpath);
				} 
				catch (IOException x) {
					System.out.println("Bad directory: " + x);
					return null;
				}
			else
				return null;
		}
		
		XElement dcdb = XElement.tag("dcDatabase")
				.withAttribute("Path", dbpath.toString())
				.withAttribute("BackupPath", dbbakpath.toString());

		ConnectionManager dm = new ConnectionManager();		// TODO generalize, support more than rocksdb
		dm.load(dcdb);
		
		// but do not start because that gets a backup going
		
		return dm;
	}
}
