package dcraft.db.proc;

import dcraft.db.tables.TablesAdapter;
import dcraft.hub.op.OperatingContextException;

public interface ITrigger {
	// false to cancel event - only works if this is a "before" trigger
	boolean execute(TablesAdapter db, String table, String id) throws OperatingContextException;
}
