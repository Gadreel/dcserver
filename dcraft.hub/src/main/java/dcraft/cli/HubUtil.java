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

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Locale;
import java.util.Scanner;

import dcraft.api.ApiSession;
import dcraft.db.Constants;
import dcraft.db.DatabaseAdapter;
import dcraft.db.DatabaseException;
import dcraft.db.DbServiceRequest;
import dcraft.db.request.DataRequest;
import dcraft.db.request.common.AddUserRequest;
import dcraft.db.request.common.RequestFactory;
import dcraft.db.rocks.Adapter;
import dcraft.db.rocks.ConnectionManager;
import dcraft.db.rocks.keyquery.KeyQuery;
import dcraft.db.tables.TablesAdapter;
import dcraft.db.util.ByteUtil;
import dcraft.db.util.DbUtil;
import dcraft.hub.Foreground;
import dcraft.hub.ILocalCommandLine;
import dcraft.hub.ResourceHub;
import dcraft.hub.app.ApplicationHub;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationOutcomeStruct;
import dcraft.log.Logger;
import dcraft.struct.BaseStruct;
import dcraft.struct.Struct;
import dcraft.util.*;
import dcraft.util.pgp.KeyRingCollection;
import dcraft.xml.XElement;
import org.bouncycastle.openpgp.PGPPublicKeyRing;
import org.rocksdb.BackupInfo;
import org.rocksdb.RocksIterator;

import static dcraft.db.Constants.*;

public class HubUtil implements ILocalCommandLine {
	/*
	 * Consider developing CLI further with libraries like these:
	 * 
	 * https://github.com/jline/jline3
	 * https://github.com/fusesource/jansi
	 * 
	 */
	@Override
	public void run(Scanner scan, ApiSession api) {
		boolean running = true;

		while(running) {
			try {
				System.out.println();
				System.out.println("-----------------------------------------------");
				System.out.println("   Hub General Utils");
				System.out.println("-----------------------------------------------");
				System.out.println("0)   Exit");
				System.out.println("1)   dcDatabase Utils");
				System.out.println("2)   Local Utilities");
				System.out.println("3)   Crypto Utilities");
				System.out.println("100) dcScript GUI Debugger");
				System.out.println("101) dcScript Run Script");

				String opt = scan.nextLine();
				
				Long mopt = StringUtil.parseInt(opt);
				
				if (mopt == null)
					continue;
				
				switch (mopt.intValue()) {
				case 0: {
					running = false;
					break;
				}
				
				case 1: {
					this.utilityMenu(scan);
					break;
				}
				
				case 2: {
					Foreground.utilityMenu(scan);					
					break;
				}
				
				case 3: {
					this.cryptoMenu(scan);
					break;
				}
				
				/*
				case 100: {
					ScriptUtility.goSwing(null);					
					break;
				}
				*/

				/*
				case 101: {
					System.out.println("*** Run A dcScript ***");
					System.out.println("If you are looking for something to try, consider one of these:");
					System.out.println("  ./packages/dcTest/dcs/examples/99-bottles.dcs.xml");
					System.out.println("  ./packages/dcTest/dcs/examples/99-bottles-debug.dcs.xml");
					
					System.out.println();
					System.out.println("Path to scriptold to run: ");
					String spath = scan.nextLine();
			    	
					System.out.println();
					
					FuncResult<CharSequence> rres = IOUtil.readEntireFile(Paths.get(spath));
					
					if (rres.hasErrors()) {
						System.out.println("Error reading scriptold: " + rres.getMessage());
						break;
					}
					
					Activity act = new Activity();
					
					OperationResult compilelog = act.compile(rres.getResult().toString());
					
					if (compilelog.hasErrors()) {
						System.out.println("Error compiling scriptold: " + compilelog.getMessage());
						break;
					}
					
					Task task = Task.taskWithRootContext()
						.withTitle(act.getScript().getXml().getAttribute("Title", "Debugging dcScript"))	
						.withTimeout(0)							// no timeout in editor mode
						.withWork(act);
					
					Hub.instance.getWorkPool().submit(task);
					
					break;
				}
				*/
				
				}
			}
			catch (Exception x) {
				System.out.println("Command Line Error: " + x);
			}
		}
	}

	public void utilityMenu(Scanner scan) {
		boolean running = true;
		
		while(running) {
			try {
				System.out.println();
				System.out.println("-----------------------------------------------");
				System.out.println("   Hub " + ApplicationHub.getNodeId() + " DB Utility Menu");
				System.out.println("-----------------------------------------------");
				System.out.println("0)  Exit");
				System.out.println("1)  Database Dump");
				System.out.println("2)  Create Database");
				System.out.println("3)  Initialize Root Tenant (create db if not present)");
				System.out.println("4)  Backup Database");
				System.out.println("5)  Database Backup Info");
				System.out.println("6)  Restore Database");
				System.out.println("7)  Compact Database - TODO");
				System.out.println("8)  Mess Database");
				System.out.println("9)  Re-index dcTables");
	
				String opt = scan.nextLine();
				
				Long mopt = StringUtil.parseInt(opt);
				
				if (mopt == null)
					continue;
				
				switch (mopt.intValue()) {
				case 0:
					running = false;
					break;
				
				case 1: {
					System.out.print("Dump keys too (y/n): ");
					boolean keystoo = scan.nextLine().toLowerCase().equals("y");
				
					ConnectionManager db = this.getDatabase(scan, false);
					
					if (db == null) {
						System.out.println("Database missing or bad!");
						break;
					}					
					
					try {
						Adapter adapt = db.allocateAdapter();

						RocksIterator it = adapt.iterator();

						try {
							it.seekToFirst();
							
							while (it.isValid()) {
								byte[] key = it.key();						
								
								if (key[0] == Constants.DB_TYPE_MARKER_OMEGA) {
									System.out.println("END");
									break;
								}
								
								byte[] val = it.value();
								
								if (keystoo)
									System.out.println("Hex Key: " + HexUtil.bufferToHex(key));
								
								List<Object> keyParts = ByteUtil.extractKeyParts(key);
								
								for (Object p : keyParts)
									System.out.print((p == null) ? " / " : p.toString() + " / ");
								
								System.out.println(" = " + ByteUtil.extractValue(val));
								
								it.next();
							}
						}
						finally {
							it.close();
						}
					}
					finally {
						db.stop();
					}
					
					break;
				}
				
				case 2: {
					System.out.println("Create Database");
					ConnectionManager db = this.getDatabase(scan,  true);
					
					if (db == null) {
						System.out.println("Database missing or bad!");
						break;
					}					
					
					try {
						Adapter dbconn = db.allocateAdapter();
						
						byte[] x = dbconn.get(DB_OMEGA_MARKER_ARRAY);
						
						if (x == null) 
							System.out.println("Error creating database!");
						else
							System.out.println("Database created");
					}
					finally {
						db.stop();
					}
					
					break;
				}
				
				case 3: {
					System.out.println("Initialize Root Tenant");

					System.out.print("Obfuscator Class (empty for default): ");
					String obfclass = scan.nextLine();
					
					if (StringUtil.isEmpty(obfclass))
						obfclass = "dcraft.util.StandardSettingsObfuscator";
					
					System.out.print("Obfuscator Id (required): ");
					String clockid = scan.nextLine();
					
					if (StringUtil.isEmpty(clockid)) {
						System.out.println("required!");
						break;
					}

					System.out.print("Obfuscator Seed (empty for random): ");
					String obfseed = scan.nextLine();

					if (StringUtil.isEmpty(obfseed)) {
						byte[] feedbuff = new byte[64];
						RndUtil.random.nextBytes(feedbuff);
						obfseed = HexUtil.bufferToHex(feedbuff);
					}

					System.out.print("Global Root Password (required): ");
					String password = scan.nextLine();
					
					if (StringUtil.isEmpty(password)) {
						System.out.println("required!");
						break;
					}

					System.out.print("Root User Email (required): ");
					String email = scan.nextLine();
					
					if (StringUtil.isEmpty(email)) {
						System.out.println("required!");
						break;
					}

					ConnectionManager db = this.getDatabase(scan, true);
					
					if (db == null) {
						System.out.println("Database missing or bad!");
						break;
					}

					XElement tconfig = XElement.tag("Config")
							.with(XElement.tag("Clock")
								.withAttribute("TimerClass", obfclass)
								.withAttribute("Id", clockid)
								.withAttribute("Feed", obfseed)
							);

					DataRequest request = RequestFactory.addTenantRequest("root")
							.withParam("Password", password)
							.withParam("Config", tconfig)
							.withParam("First", "Root")
							.withParam("Last", "User")
							.withParam("Email", email);

					DbUtil.execute((DbServiceRequest) request.toDbServiceRequest()
							.withOutcome(new OperationOutcomeStruct() {
								@Override
								public void callback(BaseStruct result) throws OperatingContextException {
									System.out.println("Root added");

									db.stop();
								}
							}), db);

					break;
				}
				
				case 4: {
					System.out.println("Backup Database");
					ConnectionManager db = this.getDatabase(scan,true);
					
					if (db == null) {
						System.out.println("Database missing or bad!");
						break;
					}					
					
					try {
						db.backup();  
						System.out.println("Database backed up!");
					}
					catch (Exception x) {
						System.out.println("Error backing up database: " + x);
					}
					finally {
						db.stop();
					}
					
					break;
				}
				
				case 5: {
					System.out.println("Backup Database Stats");

					ConnectionManager db = this.getDatabase(scan, true);
					
					if (db == null) {
						System.out.println("Database missing or bad!");
						break;
					}					
					
					try {
						List<BackupInfo> list = db.dbBackup().getBackupInfo();
						
						for (BackupInfo info : list) {
							System.out.println("Backup: " + info.backupId() + " size: " + info.size() + " stamp: " + info.timestamp());
						}
					}
					catch (Exception x) {
						System.out.println("Error on database: " + x);
					}
					finally {
						db.stop();
					}
					
					break;
				}
				
				case 6: {
					System.out.println("Restore Database");
					
					/*
					ConnectionManager db = this.getDatabase(scan,true);
					
					if (db == null) {
						System.out.println("Database missing or bad!");
						break;
					}
					
					try {
						List<BackupInfo> list = db.dbBackup().getBackupInfo();
						
						for (BackupInfo info : list) {
							System.out.println("Backup: " + info.backupId() + " size: " + info.size() + " stamp: " + info.timestamp());
						}
					}
					catch (Exception x) {
						System.out.println("Error on database: " + x);
					}
					
					
					try {
						db.();
						System.out.println("Database backed up!");
					}
					catch (Exception x) {
						System.out.println("Error backing up database: " + x);
					}
					finally {
						db.stop();
					}
					*/
					
					/*
					Path dbpath = this.getDbPath(scan);
					
					if (dbpath == null) 
						break;

					Path dbBakPath = this.getDbBakPath(scan);

					//String dbbakpath = "./datastore-bak/" + dbpath.getFileName().toString();	// TODO configure location
					
					BackupableDBOptions bdb = new BackupableDBOptions(dbbakpath);
				
					RestoreBackupableDB restore = new RestoreBackupableDB(bdb);
					
					RestoreOptions ropts = new RestoreOptions(false);
					
					try {
						restore.restoreDBFromLatestBackup(dbpath.toString(), dbpath.toString(), ropts);
						restore.close();
						System.out.println("Database restored!");
					}
					catch (Exception x) {
						System.out.println("Error restoring database: " + x);
					}
					*/

					break;
				}
				
				case 7: {
					System.out.println("Compact Database");

					ConnectionManager db = this.getDatabase(scan,true);
					
					if (db == null) {
						System.out.println("Database missing or bad!");
						break;
					}					
					
					try {
						db.dbDirect().compactRange();
						System.out.println("Database compacted up!");
					}
					catch (Exception x) {
						System.out.println("Error compacting database: " + x);
					}
					finally {
						db.stop();
					}
					
					break;
				}
				
				case 8: {
					System.out.println("Messy Database");

					ConnectionManager db = this.getDatabase(scan, true);
					
					if (db == null) {
						System.out.println("Database missing or bad!");
						break;
					}					
					
					try {
						Adapter dbconn = db.allocateAdapter();
						
						BigDecimal stamp = db.allocateStamp(0);
						
						dbconn.set("root", DB_GLOBAL_RECORD, "dcTenant", DB_GLOBAL_ROOT_RECORD, "dcTitle", stamp, "Data", "BLAH!!");
						
						dbconn.set("root", DB_GLOBAL_RECORD, "dcTenant", DB_GLOBAL_ROOT_RECORD, "dcAlias", stamp, "Data", "foobar");
						
						System.out.println("Database messed up!");
					}
					catch (Exception x) {
						System.out.println("Error messing up database: " + x);
					}
					finally {
						db.stop();
					}
					
					break;
				}
				
				case 9: {
					System.out.println("Re-index db");
					/* TODO restore re-index
					Path dbpath = this.getDbPath(scan);
					
					if (dbpath == null) 
						break;

					Path dbBakPath = this.getDbBakPath(scan);

					ConnectionManager db = this.getDatabase(dbpath, dbBakPath, true);
					
					if (db == null) {
						System.out.println("Database missing or bad!");
						break;
					}					
					
					try {
						TenantManager dm = new TenantManager();
						
						dm.initFromDB(db, new OperationCallback() {							
							@Override
							public void callback() {
								UtilitiesAdapter adp = new UtilitiesAdapter(db, dm);
								adp.rebuildIndexes();
								
								System.out.println("Database indexed!");
							}
						});
					}
					catch (Exception x) {
						System.out.println("Error indexing database: " + x);
					}
					finally {
						db.stop();
					}
					*/
					
					break;
				}
				
				/*
				case 212: {
					RocksInterface adapt = ((DatabaseManager)Hub.instance.getDatabase()).allocateAdapter();
					
					KeyQuery kq = new KeyQuery(adapt, new MatchKeyLevel("Record"), 
							new MatchKeyLevel("Person"), new MatchKeyLevel(2045), new MatchKeyLevel("Name"));
					
					this.dumpQuery(kq);
					
					break;
				}
				
				case 213: {
					RocksInterface adapt = ((DatabaseManager)Hub.instance.getDatabase()).allocateAdapter();
					
					KeyQuery kq = new KeyQuery(adapt, new MatchKeyLevel("Record"), 
							new MatchKeyLevel("Person"), new MatchKeyLevel(2045), new WildcardKeyLevel());
					
					this.dumpQuery(kq);
					
					break;
				}
				
				case 214: {
					RocksInterface adapt = ((DatabaseManager)Hub.instance.getDatabase()).allocateAdapter();
					
					KeyQuery kq = new KeyQuery(adapt, new MatchKeyLevel("Record"), 
							new MatchKeyLevel("Person"), new WildcardKeyLevel(), new MatchKeyLevel("Name"));
					
					kq.setBrowseMode(true);
					
					this.dumpQuery(kq);
					
					break;
				}
				
				case 215: {
					RocksInterface adapt = ((DatabaseManager)Hub.instance.getDatabase()).allocateAdapter();
					
					KeyQuery kq = new KeyQuery(adapt, new MatchKeyLevel("Record"), 
							new WildcardKeyLevel(), new WildcardKeyLevel(), new MatchKeyLevel("Name"));
					
					this.dumpQuery(kq);
					
					break;
				}
				
				case 216: {
					RocksInterface adapt = ((DatabaseManager)Hub.instance.getDatabase()).allocateAdapter();
					
					KeyQuery kq = new KeyQuery(adapt, new WildcardKeyLevel(), 
							new MatchKeyLevel("Person"), new WildcardKeyLevel(), new MatchKeyLevel("Name"));
					
					this.dumpQuery(kq);
					
					break;
				}
				
				case 217: {
					RocksInterface adapt = ((DatabaseManager)Hub.instance.getDatabase()).allocateAdapter();
					
					KeyQuery kq = new KeyQuery(adapt, new WildcardKeyLevel(), 
							new MatchKeyLevel("Person"), new ExpandoKeyLevel());
					
					this.dumpQuery(kq);
					
					break;
				}
				
				case 218: {
					RocksInterface adapt = ((DatabaseManager)Hub.instance.getDatabase()).allocateAdapter();
					
					KeyQuery kq = new KeyQuery(adapt, new WildcardKeyLevel(), 
							new WildcardKeyLevel(), new WildcardKeyLevel());
					
					//KeyQuery kq = new KeyQuery(adapt, new WildcardKeyLevel(), 
					//		new MatchKeyLevel("Person"), new WildcardKeyLevel());
					
					//KeyQuery kq = new KeyQuery(adapt, new MatchKeyLevel("Record"), 
					//		new MatchKeyLevel("Person"), new WildcardKeyLevel());
					
					kq.setBrowseMode(true);
					
					this.dumpQuery(kq);
					
					break;
				}
				
				case 219: {
					RocksInterface adapt = ((DatabaseManager)Hub.instance.getDatabase()).allocateAdapter();
					
					System.out.println("First: ");
					
					KeyQuery kq = new KeyQuery(adapt, new MatchKeyLevel("Record"), 
							new MatchKeyLevel("Person"), new MatchKeyLevel(3045));
					
					kq.setBrowseMode(true);
					
					this.dumpQuery(kq);
					
					System.out.println("Second: ");
					
					kq = new KeyQuery(adapt, new MatchKeyLevel("Record"), 
							new MatchKeyLevel("Person"), new MatchKeyLevel(3046));
					
					kq.setBrowseMode(true);
					
					this.dumpQuery(kq);
					
					break;
				}
				
				case 220: {
					RocksInterface adapt = ((DatabaseManager)Hub.instance.getDatabase()).allocateAdapter();
					
					KeyQuery kq = new KeyQuery(adapt, new WildcardKeyLevel(), 
							new MatchKeyLevel("Person"));
					
					kq.setBrowseMode(true);
					
					this.dumpQuery(kq);
					
					break;
				}
				
				case 221: {
					RocksInterface adapt = ((DatabaseManager)Hub.instance.getDatabase()).allocateAdapter();
					
					KeyQuery kq = new KeyQuery(adapt, new WildcardKeyLevel());
					
					kq.setBrowseMode(true);
					
					this.dumpQuery(kq);
					
					break;
				}
				
				case 222: {
					RocksInterface adapt = ((DatabaseManager)Hub.instance.getDatabase()).allocateAdapter();
					
					KeyQuery kq = new KeyQuery(adapt, new WildcardKeyLevel(),
							new WildcardKeyLevel());
					
					kq.setBrowseMode(true);
					
					this.dumpQuery(kq);
					
					break;
				}
				
				case 223: {
					Hub.instance.getDatabase().submit(new KeyQueryRequest(), new ObjectResult() {
						@Override
						public void process(CompositeStruct result) {
							System.out.println("KeyQuery returned: " + result);
						}
					});
					
					break;
				}
				
				case 224: {
					DbRecordRequest req = new InsertRecordRequest()
							.withTable(DB_GLOBAL_TENANT_DB)
							.withSetField("dcTitle", "Betty Site")
							.withSetField("dcName", "betty.com", "betty.com")
							.withSetField("dcName", "www.betty.com", "www.betty.com")
							.withSetField("dcDescription", "Website for Betty Example");
							
					Hub.instance.getDatabase().submit(req, new ObjectResult() {
						@Override
						public void process(CompositeStruct result) {
							System.out.println("InsertRecordRequest returned: " + result);
						}
					});
					
					break;
				}
				
				case 225: {
					DbRecordRequest req = new UpdateRecordRequest()
							.withTable(DB_GLOBAL_TENANT_DB)
							.withId("00100_000000000000001")
							.withSetField("dcName", "mail.betty.com", "mail.betty.com")			// add mail
							.withSetField("dcName", "www.betty.com", "web.betty.com")			// change www to web
							.withRetireField("dcName", "betty.com")								// retire a name
							.withSetField("dcDescription", "Website for Betty Example 2");		// update a field		
							
					Hub.instance.getDatabase().submit(req, new ObjectResult() {
						@Override
						public void process(CompositeStruct result) {
							System.out.println("UpdateRecordRequest returned: " + result);
						}
					});
					
					break;
				}
				
				case 226: {
					// alternative syntax
					DbRecordRequest req = new InsertRecordRequest()
						.withTable(DB_GLOBAL_TENANT_DB)
						.withSetField("dcTitle", "Mandy Site")
						.withSetField("dcDescription", "Website for Mandy Example")
						.withSetField("dcName", "mandy.com", "mandy.com")
						.withSetField("dcName", "www.mandy.com", "www.mandy.com");
							
					Hub.instance.getDatabase().submit(req, new ObjectResult() {
						@Override
						public void process(CompositeStruct result) {
							System.out.println("InsertRecordRequest returned: " + result);
						}
					});
					
					break;
				}
				
				case 227: {
					DbRecordRequest req = new InsertRecordRequest()
							.withTable("dcUser")
							// all DynamicScalar, but suibid is auto assigned
							.withSetField("dcUsername", "mblack")
							.withSetField("dcEmail", "mblack@mandy.com")
							.withSetField("dcFirstName", "Mandy")
							.withSetField("dcLastName", "Black");
					
					Hub.instance.getDatabase().submit(req, new ObjectResult() {
						@Override
						public void process(CompositeStruct result) {
							System.out.println("InsertRecordRequest returned: " + result);
						}
					});
					
					break;
				}
				
				case 228: {
					System.out.println("Last name sid: ");
					String subid = scan.nextLine();
					
					DbRecordRequest req = new UpdateRecordRequest()
						.withTable("dcUser")
						.withId("00100_000000000000001")
						.withSetField("dcLastName", subid, "Blackie");
					
					Hub.instance.getDatabase().submit(req, new ObjectResult() {
						@Override
						public void process(CompositeStruct result) {
							System.out.println("UpdateRecordRequest returned: " + result);
						}
					});
					
					DbRecordRequest ireq = new InsertRecordRequest()
						.withTable("dcUser")
						// all DynamicScalar, but suibid is auto assigned
						.withSetField("dcUsername", "xblackie")
						.withSetField("dcEmail", "xblackie@mandy.com")
						.withSetField("dcFirstName", "Charles")
						.withSetField("dcLastName", "Blackie");
					
					Hub.instance.getDatabase().submit(ireq, new ObjectResult() {
						@Override
						public void process(CompositeStruct result) {
							System.out.println("InsertRecordRequest returned: " + result);
						}
					});
					
					break;
				}
				
				case 229: {
					
					DataRequest req = new DataRequest("dcPing");
					
					Hub.instance.getDatabase().submit(req, new ObjectResult() {
						@Override
						public void process(CompositeStruct result) {
							System.out.println("PingRequest 1 returned: " + result);
						}
					});
					
					Hub.instance.getDatabase().submit(RequestFactory.ping(), new ObjectResult() {
						@Override
						public void process(CompositeStruct result) {
							System.out.println("PingRequest 2 returned: " + result);
						}
					});
					
					
					break;
				}
				
				case 230: {
					System.out.println("Echo phrase: ");
					String in = scan.nextLine();
					
					Hub.instance.getDatabase().submit(RequestFactory.echo(in), new ObjectResult() {
						@Override
						public void process(CompositeStruct result) {
							System.out.println("EchoRequest returned: " + result);
						}
					});
					
					break;
				}
				
				case 231: {
					LoadRecordRequest req = new LoadRecordRequest()
						.withTable(DB_GLOBAL_TENANT_DB)
						.withId(OperationContext.get().getUserContext().getTenantId()) 
						.withSelect(new SelectFields()
							.withField("dcTitle", "SiteName")
							.withField("dcDescription", "Description")
							.withField("dcName", "Names")
						);
					
					Hub.instance.getDatabase().submit(req, new ObjectResult() {
						@Override
						public void process(CompositeStruct result) {
							System.out.println("LoadRecordRequest returned: " + result);
						}
					});
					
					break;
				}
				
				case 232: {
					RecordStruct params = new RecordStruct(
							new FieldStruct("ExpireThreshold", new DateTime().minusMinutes(3)),
							new FieldStruct("LongExpireThreshold", new DateTime().minusMinutes(5))
					);
					
					
					Hub.instance.getDatabase().submit(new DataRequest("dcCleanup").withParams(params), new ObjectResult() {							
						@Override
						public void process(CompositeStruct result) {
							if (this.hasErrors())
								Logger.errorTr(114);
						}
					});
					
				}
				
				
				case 239: {
					ListDirectRequest req = new ListDirectRequest("dcUser", new SelectField()
						.withField("dcUsername"));
					
					Hub.instance.getDatabase().submit(req, new ObjectResult() {
						@Override
						public void process(CompositeStruct result) {
							System.out.println("ListDirectRequest returned: " + result);
						}
					});
					
					break;
				}
				*/
				}
			}
			catch(Exception x) {
				System.out.println("CLI error: " + x);
			}
		}		
	}
	
	public ConnectionManager getDatabase(Scanner scan, boolean createIfNotPresent) {
		System.out.print("Direct path (y/n): ");
		boolean direct = scan.nextLine().toLowerCase().equals("y");
		
		if (direct) {
			System.out.print("Enter name (or path) of database: ");
			String name = scan.nextLine();
			
			if (StringUtil.isEmpty(name) || StringUtil.isDataInteger(name) || "0".equals(name))
				return null;
			
			// if the path is only the folder name and nothing more, put it in ./datastore
			if (! name.contains("/"))
				name = "./datastore/" + name;
			
			Path dbpath = Paths.get(name);
			
			if (dbpath == null)
				return null;
			
			System.out.print("Enter name (or path) of database backup: ");
			String bakname = scan.nextLine();
			
			if (StringUtil.isEmpty(bakname) || StringUtil.isDataInteger(bakname) || "0".equals(bakname))
				return null;
			
			// if the path is only the folder name and nothing more, put it in ./datastore
			if (! bakname.contains("/"))
				bakname = "./datastore-bak/" + bakname;
			
			Path dbBakPath = Paths.get(bakname);
			
			return this.getDatabase(dbpath, dbBakPath, createIfNotPresent);
		}
		
		System.out.print("Deployment: ");
		String deployment = scan.nextLine();
		
		System.out.println();
		
		System.out.print("Node: ");
		String node = scan.nextLine();
		
		System.out.println();
		
		System.out.print("Database (enter for default): ");
		String db = scan.nextLine();
		
		System.out.println();
		
		if (StringUtil.isEmpty(db))
			db = "default";
		
		Path npath = Paths.get("./deploy-" + deployment + "/nodes/" + node);
		
		return this.getDatabase(npath.resolve("database").resolve(db), npath.resolve("database-bak").resolve(db), createIfNotPresent);
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

	public void dumpQuery(KeyQuery kq) {
		while (kq.nextKey() != null) {
			byte[] key = kq.key();						
			
			if (key[0] == Constants.DB_TYPE_MARKER_OMEGA) {
				System.out.println("END");
				break;
			}
			
			byte[] val = kq.value();
			
			List<Object> keyParts = ByteUtil.extractKeyParts(key);
			
			for (Object p : keyParts)
				System.out.print(p.toString() + " / ");
			
			System.out.println(" = " + ByteUtil.extractValue(val));
		}
	}

	public void cryptoMenu(Scanner scan) {
		boolean running = true;
		
		while(running) {
			try {
				System.out.println();
				System.out.println("-----------------------------------------------");
				System.out.println("   Hub " + ApplicationHub.getNodeId() + " Crypto Utility Menu");
				System.out.println("-----------------------------------------------");
				System.out.println("0)  Exit");
				System.out.println("1)  Cipher Dump");
				System.out.println("2)  Prep Clock");
				System.out.println("50)  Init deploy keys");
				System.out.println("51)  Set node keys");
				System.out.println("52)  Set tenant keys");
	
				String opt = scan.nextLine();
				
				Long mopt = StringUtil.parseInt(opt);
				
				if (mopt == null)
					continue;
				
				switch (mopt.intValue()) {
				case 0:
					running = false;
					break;
				
					/*
				case 1: {
			        String protocol = "TLSv1.2";
		            SSLContext serverContext = SSLContext.getInstance(protocol);
		            serverContext.init(null, null, null);
		            
		            SSLEngine engine = serverContext.createSSLEngine();
			        
			        System.out.println("Enabled");
			        
			        for (String p : engine.getEnabledProtocols())
			        	System.out.println("Proto: " + p);
			        
			        for (String p : engine.getEnabledCipherSuites())
			        	System.out.println("Suite: " + p);
			        
			        System.out.println();        
			        System.out.println("Supported");
			        System.out.println();        
			        
			        for (String p : engine.getSupportedProtocols())
			        	System.out.println("Proto: " + p);
			        
			        for (String p : engine.getSupportedCipherSuites())
			        	System.out.println("Suite: " + p);
					
					break;
				}
				 */
				
					case 2: {
						XElement clock = StandardSettingsObfuscator.obfus().configure(null, null);
						
						System.out.println(clock.toPrettyString());
						
						break;
					}
					
					case 50: {
						System.out.print("Deployment: ");
						String deployment = scan.nextLine();
						
						System.out.println();
						
						System.out.print("Password: ");
						String pw = scan.nextLine();
						
						System.out.println();
						
						Path cpath = Paths.get("./deploy-" + deployment);
						
						HubUtil.initDeployKeys(cpath, deployment, pw);
						
						break;
					}
					
					case 51: {
						System.out.print("Deployment: ");
						String deployment = scan.nextLine();
						
						System.out.println();
						
						System.out.print("Node: ");
						String node = scan.nextLine();
						
						System.out.println();
						
						System.out.print("Password (use same): ");
						String pw = scan.nextLine();
						
						System.out.println();
						
						Path cpath = Paths.get("./deploy-" + deployment);
						
						HubUtil.initDeployNodeKeys(cpath, deployment, node, pw);
						
						break;
					}
					
					case 52: {
						System.out.print("Deployment: ");
						String deployment = scan.nextLine();
						
						System.out.println();
						
						System.out.print("Tenant: ");
						String tenant = scan.nextLine();
						
						System.out.println();
						
						System.out.print("Password (use same): ");
						String pw = scan.nextLine();
						
						System.out.println();
						
						Path cpath = Paths.get("./deploy-" + deployment);
						
						this.initDeployTenantKeys(cpath, deployment, tenant, pw);
						
						break;
					}
				
				}
			}
			catch(Exception x) {
				System.out.println("CLI error: " + x);
			}
		}
	}
	
	static public void initDeployKeys(Path path, String deployment, String passphrase) {
		Path cpath = path.resolve("config");
		Path ipath = path.resolve("roles/ignite/config");
		
		try {
			Files.createDirectories(cpath);
			Files.createDirectories(ipath);
		}
		catch (IOException x) {
			System.out.println("error: " + x);
			return;
		}
		
		KeyRingCollection keyring = KeyRingCollection.load(cpath, true);
		
		PGPPublicKeyRing pgpPublicKeyRing2 = keyring.createKeyPairAddToRing("encryptor@" + deployment + ".dc", passphrase);
		
		System.out.println("new encryptor key: " + HexUtil.bufferToHex(pgpPublicKeyRing2.getPublicKey().getFingerprint()));
		
		KeyRingCollection ikeyring = KeyRingCollection.load(ipath, true);
		
		// only on the orchestration node, not on public
		PGPPublicKeyRing pgpPublicKeyRing3 = ikeyring.createKeyPairAddToRing("ignite@" + deployment + ".dc", passphrase);
		
		System.out.println("new ignite key: " + HexUtil.bufferToHex(pgpPublicKeyRing3.getPublicKey().getFingerprint()));
		
		ikeyring.save();
		
		keyring.addPublicKey(pgpPublicKeyRing3, false);
		
		keyring.save();
	}
	
	static public void initDeployNodeKeys(Path path, String deployment, String node, String passphrase) {
		Path cpath = path.resolve("config");
		Path npath = path.resolve("nodes/" + node + "/config");
		
		try {
			Files.createDirectories(cpath);
			Files.createDirectories(npath);
		}
		catch (IOException x) {
			System.out.println("error: " + x);
			return;
		}
		
		KeyRingCollection nkeyring = KeyRingCollection.load(npath, true);
		
		PGPPublicKeyRing pgpPublicKeyRing = nkeyring.createKeyPairAddToRing(node + "-signer@" + deployment + ".dc", passphrase);
		
		System.out.println("new node sign key: " + HexUtil.bufferToHex(pgpPublicKeyRing.getPublicKey().getFingerprint()));
		
		nkeyring.save();
		
		// share public with all
		KeyRingCollection keyring = KeyRingCollection.load(cpath, true);
		
		keyring.addPublicKey(pgpPublicKeyRing,false);
		
		keyring.save();
	}
	
	static public void initDeployTenantKeys(Path path, String deployment, String tenant, String passphrase) {
		Path cpath = path.resolve("config");
		Path npath = path.resolve("tenants/" + tenant + "/config");
		
		try {
			Files.createDirectories(npath);
		}
		catch (IOException x) {
			System.out.println("error: " + x);
			return;
		}
		
		KeyRingCollection nkeyring = KeyRingCollection.load(npath, true);
		
		PGPPublicKeyRing pgpPublicKeyRing = nkeyring.createKeyPairAddToRing(tenant + "-secure@" + deployment + ".dc", passphrase);
		
		System.out.println("new tenant security key: " + HexUtil.bufferToHex(pgpPublicKeyRing.getPublicKey().getFingerprint()));
		
		nkeyring.save();
		
		// share public with all
		KeyRingCollection keyring = KeyRingCollection.load(cpath, true);
		
		keyring.addPublicKey(pgpPublicKeyRing,false);
		
		keyring.save();
	}
}
