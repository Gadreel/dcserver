package dcraft.cms.thread.db.access;

import dcraft.cms.thread.IChannelAccess;
import dcraft.db.tables.TablesAdapter;
import dcraft.hub.op.IVariableAware;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
import dcraft.struct.Struct;

import java.util.ArrayList;
import java.util.List;

public class UsrAccess implements IChannelAccess {
	@Override
	public List<String> collectParties(TablesAdapter adapter, IVariableAware scope) throws OperatingContextException {
		List<String> parties = new ArrayList<>();
		
		parties.add("/Usr/" + OperationContext.getOrThrow().getUserContext().getUserId());
		
		return parties;
	}
	
	@Override
	public String formatParty(TablesAdapter adapter, IVariableAware scope, String party) throws OperatingContextException {
		String uid = party.substring(5);
		
		return Struct.objectToString(adapter.getScalar("dcUser", uid, "dcFirstName")) + " "
				+ Struct.objectToString(adapter.getScalar("dcUser", uid, "dcLastName"));
	}
}
