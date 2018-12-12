package dcraft.cms.thread;

import dcraft.db.tables.TablesAdapter;
import dcraft.hub.op.IVariableAware;
import dcraft.hub.op.OperatingContextException;

import java.util.List;

public interface IChannelAccess {
	List<String> collectParties(TablesAdapter adapter, IVariableAware scope) throws OperatingContextException;
}
