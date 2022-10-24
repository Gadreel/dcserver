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
import dcraft.hub.app.ApplicationHub;
import dcraft.hub.op.*;
import dcraft.hub.resource.KeyRingResource;
import dcraft.interchange.aws.AWSEC2;
import dcraft.log.Logger;
import dcraft.mail.SmtpWork;
import dcraft.service.ServiceHub;
import dcraft.service.ServiceRequest;
import dcraft.service.work.InBoxQueuePollWork;
import dcraft.sql.SqlConnection;
import dcraft.sql.SqlUtil;
import dcraft.sql.SqlWriter;
import dcraft.struct.*;
import dcraft.struct.scalar.StringStruct;
import dcraft.task.*;
import dcraft.task.run.WorkHub;
import dcraft.tool.sentinel.AccessUtil;
import dcraft.tool.sentinel.EmailActivityPollWork;
import dcraft.tool.sentinel.EmailActivityProcessWork;
import dcraft.service.portable.PortableMessageUtil;
import dcraft.tool.sentinel.MultiInBoxPollWork;
import dcraft.util.*;
import dcraft.util.io.OutputWrapper;
import dcraft.util.pgp.ClearsignUtil;
import dcraft.xml.XElement;
import org.bouncycastle.openpgp.PGPLiteralDataGenerator;
import org.bouncycastle.openpgp.PGPSecretKeyRing;
import org.bouncycastle.openpgp.PGPUtil;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.temporal.TemporalAccessor;
import java.util.Date;
import java.util.Scanner;

public class Sentinel implements ILocalCommandLine {
	@Override
	public void run(Scanner scan, ApiSession client) throws Exception {
		boolean running = true;

		while (running) {
			try {
				System.out.println();
				System.out.println("-----------------------------------------------");
				System.out.println("   dcSENTINEL main menu");
				System.out.println("-----------------------------------------------");
				System.out.println("0)   Exit");
				System.out.println("1)   testing");
				System.out.println("20)  Hub Utilities");
				System.out.println("112) Update IP access");
				System.out.println("113) Review IP access");

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

			case 20: {
				new HubUtil().run(scan, client);
				break;
			}

			case 100: {
				/*
				try (SqlConnection conn = SqlUtil.getConnection("sentinel-readwrite")) {
					System.out.println(("rw: " + conn.getVarString("SELECT FirstName FROM dca_user WHERE Id = ?", 1)));
				}

				try (SqlConnection conn = SqlUtil.getConnection("sentinel-readonly")) {
					System.out.println(("ro: " + conn.getVarString("SELECT FirstName FROM dca_user WHERE Id = ?", 1)));
				}


				try (SqlConnection conn = SqlUtil.getConnection("sentinel-direct")) {
					conn.getResults("SELECT Config FROM test_table");

					long st = System.currentTimeMillis();

					System.out.println("start: " + st);

					for (int i = 1; i < 11; i++) {
							System.out.println(("#" + i + ": " + conn.getVarString("SELECT Config FROM test_table WHERE Id = ?", i)));
						System.out.println("sub time: " + (System.currentTimeMillis() - st));
					}

					ListStruct res = conn.getResults("SELECT Config FROM test_table");
					System.out.println("sub time: " + (System.currentTimeMillis() - st));

					System.out.println("got: " + res.size());

					long et = System.currentTimeMillis();

					System.out.println("end: " + et);

					System.out.println("time: " + (et - st));
				}
				 */

				try (SqlConnection conn = SqlUtil.getConnection("sentinel-readwrite")) {
					ListStruct awsaccounts = conn.getResults("SELECT acc.Id, acc.Alias FROM dca_aws_account acc");

					for (int i = 0; i < awsaccounts.size(); i++) {
						RecordStruct account = awsaccounts.getItemAsRecord(i);

						String id = account.getFieldAsString("Id");
						String alias = account.getFieldAsString("Alias");

						XElement settings = ApplicationHub.getCatalogSettings("Interchange-Aws-" + alias);

						if (settings == null) {
							System.out.println("Missing settings Interchange-Aws for: " + id);
							continue;
						}

						System.out.println("got settings for: " + alias + " - " + settings.attr("KeyId"));
					}
				}

				break;
			}

			case 101: {
				TaskHub.submit(Task.ofSubtask("Email Activity Poll", "TEST")
								.withWork(
										EmailActivityPollWork.of()
								),
						new TaskObserver() {
							@Override
							public void callback(TaskContext subtask) {
								System.out.println("EmailActivityPoll done!");
							}
						});

				break;
			}

			case 102: {
				TaskHub.submit(Task.ofSubtask("Email Activity Process", "TEST")
								.withWork(
										EmailActivityProcessWork.of()
								),
						new TaskObserver() {
							@Override
							public void callback(TaskContext subtask) {
								System.out.println("Email Activity Process done!");
							}
						});

				break;
			}

			case 103: {
				ServiceRequest report = ServiceRequest.of("dcmServices.Reports.OutboundEmailActivity")
						.withData(RecordStruct.record()
								.with("ReportId", 18)
								.with("ReportAt", TimeUtil.now())
								.with("ReportType", 1)
								.with("Message", RecordStruct.record()
										.with("reportType", "Delivery")
								)
						);

				RecordStruct rmsg = RecordStruct.record()
						.with("MessageId", RndUtil.nextUUId())
						.with("Version",  "2022.1")
						.with("Source", ApplicationHub.getDeployment() + "/" + OperationContext.getOrThrow().getTenant().getAlias() + "/" + ApplicationHub.getNodeId())
						.with("Destination", "sentinel/root")
						.with("Timestamp", TimeUtil.now())
						.with("Expires", TimeUtil.now().plusDays(2))
						.with("Payload", report.toRecord());

				String msg = rmsg.toPrettyString();

				KeyRingResource keyring = ResourceHub.getResources().getKeyRing();

				PGPSecretKeyRing seclocalsign = keyring.findUserSecretKey("encryptor@sentinel.dc");

				ClearsignUtil.ClearSignResult cres = ClearsignUtil.clearSignMessage(msg, keyring, seclocalsign, keyring.getPassphrase());

				IOUtil.saveEntireFile(Path.of("./temp/clear-sign-rpc.jasc"), cres.file);

				break;
			}

			case 104: {
				KeyRingResource keys = ResourceHub.getResources().getKeyRing();

				StringBuilder sb = new StringBuilder();
				StringStruct sig = StringStruct.ofEmpty();
				StringStruct key = StringStruct.ofEmpty();

				try (InputStream bais = Files.newInputStream(Path.of("./temp/clear-sign-rpc.jasc"), StandardOpenOption.READ)) {
					ClearsignUtil.verifyFile(bais, keys, sb, sig, key);
				}

				if (sig.isEmpty()) {
					System.out.println("failed");
				}
				else {
					System.out.println("success, key: " + key);

					CompositeStruct croot = CompositeParser.parseJson(sb);

					System.out.println("output: " + croot.toPrettyString());
				}

				break;
			}

			case 105: {
				String timestamp = "2021-12-30T19:15:14.843Z";
				TemporalAccessor dt = Instant.parse(timestamp);

				System.out.println("dt: " + dt);

				break;
			}

			case 106: {
				/*
				ServiceHub.call(ServiceRequest.of("dcCoreServices.Status.Ping"), new OperationOutcomeStruct() {
					@Override
					public void callback(BaseStruct result) throws OperatingContextException {
						System.out.println("got: " + result);
					}
				});
				 */

				RecordStruct replypayload = RecordStruct.record()
						.with("Op", "dcCoreServices.Status.Print")
						.with("IncludeResult", true);

				PortableMessageUtil.sendMessageToRemoteTenantByQueue(3L, "dcCoreServices.Status.Ping", null, 0L, replypayload, null);

				break;
			}

			case 107: {
				System.out.println("Message to tenant id: ");
				String tid = scan.nextLine();

				if (StringUtil.isEmpty(tid))
					break;

				Long ltid = StringUtil.parseInt(tid);

				if (ltid == null)
					break;

				System.out.println("Message to echo: ");
				String address = scan.nextLine();

				if (StringUtil.isEmpty(address))
					break;

				/*
				ServiceHub.call(ServiceRequest.of("dcCoreServices.Status.Echo").withData(StringStruct.of(address)), new OperationOutcomeStruct() {
					@Override
					public void callback(BaseStruct result) throws OperatingContextException {
						System.out.println("got: " + result);
					}
				});
				 */

				RecordStruct replypayload = RecordStruct.record()
						.with("Op", "dcCoreServices.Status.Print")
						.with("IncludeResult", true);

				PortableMessageUtil.sendMessageToRemoteTenantByQueue(ltid, "dcCoreServices.Status.Echo", StringStruct.of(address), 0L, replypayload, null);

				break;
			}

			case 108: {
				TaskHub.submit(
						Task.ofHubRoot()
								.withTitle("Manually try the inbox message queue.")
								.withWork(new MultiInBoxPollWork()),
						new TaskObserver() {
							@Override
							public void callback(TaskContext task) {
								System.out.println("manual try complete");
							}
						}
				);

				break;
			}

			case 110: {

				TaskHub.submit(Task.ofSubtask("Test TAR", "XFR")
								.withParams(RecordStruct.record()
										.with("To", "lightofgadrel@gmail.com")
										.with("Subject", "test the DCA AWS send mail")
										.with("Html", "<html><body>testing to andy 5</body></html>")
										.with("Text", "testing to andy 5")
										.with("Feedback", true)
								)
								.withWork(
										ChainWork
												.of(new IWork() {
													@Override
													public void run(TaskContext taskctx) throws OperatingContextException {
														System.out.println("before send");

														// pass on params
														taskctx.returnValue(taskctx.getParams());
													}
												})
												.then(new SmtpWork())
								),
						new TaskObserver() {
							@Override
							public void callback(TaskContext subtask) {
								System.out.println("Email sent!");

								System.out.println("Result: " + subtask.getResult());
							}
						});

				break;


			}

			case 111: {
				ServiceRequest report = ServiceRequest.of("dcmServices.Reports.OutboundEmailActivity")
						.withData(RecordStruct.record()
								.with("ReportId", 18)
								.with("ReportAt", TimeUtil.now())
								.with("ReportType", 1)
								.with("Message", RecordStruct.record()
										.with("reportType", "Delivery")
								)
						);

				RecordStruct rmsg = RecordStruct.record()
						.with("MessageId", RndUtil.nextUUId())
						.with("Version",  "2022.1")
						.with("Source", ApplicationHub.getDeployment() + "/" + OperationContext.getOrThrow().getTenant().getAlias() + "/" + ApplicationHub.getNodeId())
						.with("Destination", "sentinel/root")
						.with("Timestamp", TimeUtil.now())
						.with("Expires", TimeUtil.now().plusDays(2))
						.with("Payload", report.toRecord());

				String msg = rmsg.toPrettyString();

				KeyRingResource keyring = ResourceHub.getResources().getKeyRing();

				PGPSecretKeyRing seclocalsign = keyring.findUserSecretKey("encryptor@prod-3.dc");

				Memory mem = new Memory();

				OutputStream os = new OutputWrapper(mem);

				Date now = new Date();

				PGPLiteralDataGenerator encrypt = new PGPLiteralDataGenerator();

				OutputStream eos = encrypt.open(os, 'U', "data", msg.length(), now);

				eos.write(msg.getBytes(StandardCharsets.UTF_8));

				eos.close();

				// TODO
				//IOUtil.saveEntireFile(Path.of("./temp/clear-sign-rpc.jasc"), cres.file);

				break;
			}

			case 112: {
				System.out.println("Update developer IP access");
				System.out.println("");

				ListStruct users = AccessUtil.getUsersV1();

				for (int i = 0; i < users.size(); i++) {
					RecordStruct user = users.getItemAsRecord(i);

					String username = user.getFieldAsString("Username");

					System.out.println((i + 1) + ") " + username);
				}

				System.out.println("");
				System.out.print("Enter User #: ");

				String tid = scan.nextLine();

				if (StringUtil.isEmpty(tid))
					break;

				Long ltid = StringUtil.parseInt(tid);

				if ((ltid == null) || (ltid == 0))
					break;

				RecordStruct seluser = users.getItemAsRecord(ltid.intValue() - 1);

				if (seluser == null)
					break;

				Long userid = seluser.getFieldAsInteger("Id");

				ListStruct rules = AccessUtil.getUserIPRulesV1(userid);

				for (int i = 0; i < rules.size(); i++) {
					RecordStruct rule = rules.getItemAsRecord(i);

					String label = rule.getFieldAsString("Label");

					System.out.println((i + 1) + ") " + label + " - " + rule.getFieldAsString("IPv4Address"));
				}

				System.out.println("");
				System.out.print("Enter Label #: ");

				String lid = scan.nextLine();

				if (StringUtil.isEmpty(lid))
					break;

				Long llid = StringUtil.parseInt(lid);

				if ((llid == null) || (llid == 0))
					break;

				RecordStruct selrule = rules.getItemAsRecord(llid.intValue() - 1);

				if (selrule == null)
					break;

				Long ruleid = selrule.getFieldAsInteger("Id");

				System.out.println("");
				System.out.print("Enter New IPv4 Address: ");

				String ipv4 = scan.nextLine();

				if (StringUtil.isEmpty(ipv4) || "0".equals(ipv4))
					break;

				AccessUtil.updateUserAccessV1(ipv4, ruleid, seluser.getFieldAsString("Username") + " - " + selrule.getFieldAsString("Label"), new OperationOutcomeEmpty() {
					@Override
					public void callback() throws OperatingContextException {
						System.out.println("Finished");
					}
				});

				System.out.println("Finishing");

				break;
			}

			case 113: {
				System.out.println("Review developer IP access");
				System.out.println("");

				ListStruct users = AccessUtil.getUsersV1();

				for (int i = 0; i < users.size(); i++) {
					RecordStruct user = users.getItemAsRecord(i);

					String username = user.getFieldAsString("Username");

					System.out.println((i + 1) + ") " + username);
				}

				System.out.println("");
				System.out.print("Enter User #: ");

				String tid = scan.nextLine();

				if (StringUtil.isEmpty(tid))
					break;

				Long ltid = StringUtil.parseInt(tid);

				if ((ltid == null) || (ltid == 0))
					break;

				RecordStruct seluser = users.getItemAsRecord(ltid.intValue() - 1);

				if (seluser == null)
					break;

				Long userid = seluser.getFieldAsInteger("Id");

				ListStruct rules = AccessUtil.getUserIPRulesV1(userid);

				for (int i = 0; i < rules.size(); i++) {
					RecordStruct rule = rules.getItemAsRecord(i);

					String label = rule.getFieldAsString("Label");

					System.out.println((i + 1) + ") " + label + " - " + rule.getFieldAsString("IPv4Address"));
				}

				System.out.println("");
				System.out.print("Enter Label #: ");

				String lid = scan.nextLine();

				if (StringUtil.isEmpty(lid))
					break;

				Long llid = StringUtil.parseInt(lid);

				if ((llid == null) || (llid == 0))
					break;

				RecordStruct selrule = rules.getItemAsRecord(llid.intValue() - 1);

				if (selrule == null)
					break;

				Long ruleid = selrule.getFieldAsInteger("Id");

				// find and update security groups

				ListStruct sgrules = AccessUtil.getUserSGRulesV1(ruleid);

				for (int i = 0; i < sgrules.size(); i++) {
					RecordStruct sgrule = sgrules.getItemAsRecord(i);

					String label = sgrule.getFieldAsString("AWSAccountTitle");

					System.out.println("Searching " + label + " - " + sgrule.getFieldAsString("AWSSecurityGroupId") + " - " + sgrule.getFieldAsString("IPv4RuleId"));

					String target = seluser.getFieldAsString("Username") + " - " + selrule.getFieldAsString("Label") + " - "  + selrule.getFieldAsString("IPv4Address");

					AccessUtil.lookupSGRuleV1(sgrule.getFieldAsString("AWSAccountAlias"), label,
							sgrule.getFieldAsString("AWSSecurityGroupId"), sgrule.getFieldAsString("IPv4RuleId"), new OperationOutcomeRecord() {
								@Override
								public void callback(RecordStruct result) throws OperatingContextException {
									if (this.isNotEmptyResult()) {
										String found = result.getFieldAsString("Description") + " - "
												+ result.getFieldAsString("FromPort") + " - "
												+ result.getFieldAsString("CidrIpv4");

										Logger.info("success: " + sgrule.getFieldAsString("AWSAccountAlias") + " for " + target + "\nfound: " + found);
									}
								}
							});
				}

				System.out.println("Finishing");

				break;
			}
		}

		return true;
	}
}
