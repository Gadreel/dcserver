package dcraft.db.proc.call;

import dcraft.db.Constants;
import dcraft.db.ICallContext;
import dcraft.db.proc.IStoredProc;
import dcraft.db.tables.TablesAdapter;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationOutcomeStruct;
import dcraft.struct.RecordStruct;
import dcraft.tenant.TenantHub;

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

		TablesAdapter db = TablesAdapter.of(request);

		request.pushTenant("root");
		db.setScalar("dcTenant", Constants.DB_GLOBAL_ROOT_RECORD,"dcGlobalPassword", hpassword);
		request.popTenant();

		callback.returnEmpty();
	}
}
