package dcraft.db.proc.call;

import dcraft.db.Constants;
import dcraft.db.ICallContext;
import dcraft.db.proc.IStoredProc;
import dcraft.db.tables.TableUtil;
import dcraft.db.tables.TablesAdapter;
import dcraft.hub.app.ApplicationHub;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationMarker;
import dcraft.hub.op.OperationOutcomeStruct;
import dcraft.hub.time.BigDateTime;
import dcraft.log.Logger;
import dcraft.session.SessionHub;
import dcraft.struct.ListStruct;
import dcraft.struct.RecordStruct;
import dcraft.struct.builder.ICompositeBuilder;
import dcraft.struct.builder.ObjectBuilder;
import dcraft.tenant.TenantHub;
import dcraft.util.ISettingsObfuscator;
import dcraft.util.StringUtil;

public class SetGlobalPassword implements IStoredProc {
	@Override
	public void execute(ICallContext request, OperationOutcomeStruct callback) throws OperatingContextException {
		RecordStruct params = request.getDataAsRecord();

		String password = params.getFieldAsString("Password");

		//ISettingsObfuscator so = TenantHub.resolveTenant("root").getObfuscator();

		//System.out.println("so 1: " + so.getConfiguration());

		String hpassword = TenantHub.resolveTenant("root").getObfuscator().hashPassword(password);

		/*
		if (TenantHub.resolveTenant("root").getObfuscator().checkHashPassword(password, hpassword)) {
			System.out.println("cool! " + hpassword);
		}
		else {
			System.out.println("darn");
		}
		*/

		TablesAdapter db = TablesAdapter.ofNow(request);

		request.pushTenant("root");
		db.setStaticScalar("dcTenant", Constants.DB_GLOBAL_ROOT_RECORD,"dcGlobalPassword", hpassword);
		request.popTenant();

		callback.returnEmpty();
	}
}
