package dcraft.cms.thread.db.access;

import dcraft.cms.thread.IChannelAccess;
import dcraft.db.tables.TablesAdapter;
import dcraft.hub.op.IVariableAware;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;

import java.util.ArrayList;
import java.util.List;

public class NoticesAccess implements IChannelAccess {
	@Override
	public List<String> collectParties(TablesAdapter adapter, IVariableAware scope) throws OperatingContextException {
		List<String> parties = new ArrayList<>();
		
		if (OperationContext.getOrThrow().getUserContext().isTagged("Admin"))
			parties.add("/NoticesPool");
		
		return parties;
	}
	
	@Override
	public String formatParty(TablesAdapter adapter, IVariableAware scope, String party) throws OperatingContextException {
		return "Notices Pool";
	}
}
