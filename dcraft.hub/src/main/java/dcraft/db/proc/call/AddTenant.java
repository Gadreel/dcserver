package dcraft.db.proc.call;

import dcraft.db.DatabaseAdapter;
import dcraft.db.DatabaseException;
import dcraft.db.DbServiceRequest;
import dcraft.db.proc.IUpdatingStoredProc;
import dcraft.db.request.common.AddUserRequest;
import dcraft.db.request.update.DbRecordRequest;
import dcraft.db.request.update.UpdateRecordRequest;
import dcraft.db.tables.TablesAdapter;
import dcraft.db.util.DbUtil;
import dcraft.hub.ResourceHub;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationOutcomeStruct;
import dcraft.log.Logger;
import dcraft.struct.ListStruct;
import dcraft.struct.RecordStruct;
import dcraft.struct.Struct;
import dcraft.task.StateWorkStep;
import dcraft.util.ISettingsObfuscator;
import dcraft.util.StandardSettingsObfuscator;
import dcraft.util.StringUtil;
import dcraft.xml.XElement;

import java.util.Locale;

import static dcraft.db.Constants.*;

// dcAddTenant
// always call from `root` tenant
public class AddTenant implements IUpdatingStoredProc {
	@Override
	public void execute(DbServiceRequest request, OperationOutcomeStruct callback) throws OperatingContextException {
		RecordStruct params = request.getDataAsRecord();
		
		String tenant = params.getFieldAsString("Alias");
		String first = params.getFieldAsString("First");
		String last = params.getFieldAsString("Last");
		String email = params.getFieldAsString("Email");
		String password = params.getFieldAsString("Password");
		XElement tconfig = params.getFieldAsXml("Config");

		if (tconfig == null)
			tconfig = XElement.tag("Config");

		// if clock is present it must hold Id, Feed and TimerClass - even if Id is same as current Top config
		XElement clock = tconfig.selectFirst("Clock");

		ISettingsObfuscator obfuscator = null;

		if (clock == null) {
			obfuscator = new StandardSettingsObfuscator();

			// use deployment level Clock for the Id - Id is shared by all tenants
			XElement clock1 = ResourceHub.getTopResources().getConfig().getTag("Clock");

			// create random value for seed
			clock = obfuscator.configure((clock1 != null) ? clock1.getAttribute("Id") : null, null);

			tconfig.with(clock);
		}
		else {
			// load tenant level obfuscator
			String obclass = clock.getAttribute("TimerClass");

			try {
				obfuscator = (ISettingsObfuscator) ResourceHub.getTopResources().getClassLoader().getInstance(obclass);
			}
			catch (Exception x) {
				Logger.error("Bad Settings Obfuscator");
				callback.returnEmpty();
				return;
			}
		}

		obfuscator.load(clock);
		
		clock.removeAttribute("Id");		// keep id only in the config file, not in database

		// ===========================================
		//  insert a template for the root user of this new Tenant
		// ===========================================

		if (! "root".equals(tenant)) {
			// this is in root Tenant
			TablesAdapter db = TablesAdapter.of(request);

			if (StringUtil.isEmpty(first))
				first = (String) db.getStaticScalar("dcUser", DB_GLOBAL_ROOT_RECORD, "dcFirstName");

			if (StringUtil.isEmpty(last))
				last = (String) db.getStaticScalar("dcUser", DB_GLOBAL_ROOT_RECORD, "dcLastName");

			if (StringUtil.isEmpty(email))
				email = (String) db.getStaticScalar("dcUser", DB_GLOBAL_ROOT_RECORD, "dcEmail");
		}
		else {
			if (StringUtil.isEmpty(first))
				first = "Root";

			if (StringUtil.isEmpty(last))
				last = "User";

			if (StringUtil.isEmpty(email))
				email = "root@localhost";
		}

		String ffirst = first;
		String flast = last;
		String femail = email;

		// insert into another tenant
		DbRecordRequest updateTenantRequest = (DbRecordRequest) UpdateRecordRequest.update()
				.withTable("dcTenant")
				.withId(DB_GLOBAL_ROOT_RECORD)
				.withSetField("dcAlias", tenant)
				.withSetField("dcConfig",tconfig.toPrettyString())
				.withForTenant(tenant);

		// add global root user password - for root only
		if ("root".equals(tenant) && StringUtil.isNotEmpty(password))
			updateTenantRequest.withSetField("dcGlobalPassword", obfuscator.hashPassword(password));

		DbUtil.execute((DbServiceRequest) updateTenantRequest.toServiceRequest()
				.withOutcome(new OperationOutcomeStruct() {
					@Override
					public void callback(Struct result) throws OperatingContextException {
						// have to use the global password until local is set
						DbRecordRequest updateUserRequest = (DbRecordRequest) UpdateRecordRequest.update()
								.withTable("dcUser")
								.withId(DB_GLOBAL_ROOT_RECORD)
								.withSetField("dcUsername", "root")
								.withSetField("dcFirstName", ffirst)
								.withSetField("dcLastName", flast)
								.withSetField("dcEmail", femail)
								.withSetField("dcConfirmed", true)
								.withSetList("dcBadges",
										ListStruct.list("SysAdmin", "Admin", "Developer")
								)
								.withForTenant(tenant);

						DbUtil.execute((DbServiceRequest) updateUserRequest.toServiceRequest()
								.withOutcome(new OperationOutcomeStruct() {
									@Override
									public void callback(Struct result) throws OperatingContextException {
										// only set this once - root may be copied but doesn't count as new record
										if ("root".equals(tenant)) {
											DatabaseAdapter dbconn = request.getInterface();

											try {
												// insert hub domain record id sequence
												dbconn.set("root", DB_GLOBAL_RECORD_META, "dcTenant", "Id", "00000", 1);

												// insert root domain record count
												dbconn.set("root", DB_GLOBAL_RECORD_META, "dcTenant", "Count", 1);

												// insert hub domain record id sequence - set to 2 because root and guest are both users - guest just isn't entered
												dbconn.set("root", DB_GLOBAL_RECORD_META, "dcUser", "Id", "00000", 2);

												// insert root domain record count
												dbconn.set("root", DB_GLOBAL_RECORD_META, "dcUser", "Count", 1);
											} catch (DatabaseException x) {
												Logger.error("Error set root meta: " + x);
											}
										}

										callback.returnEmpty();
									}
								}), request.getInterface().getManger());
					}
				}), request.getInterface().getManger());


		callback.returnEmpty();
	}
}
