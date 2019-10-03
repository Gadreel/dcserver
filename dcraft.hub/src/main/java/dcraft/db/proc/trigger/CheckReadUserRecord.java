package dcraft.db.proc.trigger;

import dcraft.db.proc.ITrigger;
import dcraft.db.tables.TablesAdapter;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
import dcraft.struct.RecordStruct;
import dcraft.struct.Struct;

public class CheckReadUserRecord implements ITrigger {
	@Override
	public boolean execute(TablesAdapter db, String table, String id, Struct context) throws OperatingContextException {
		/*
		// if we are in an RPC and got this far, then we passed the badge test
		if ((context instanceof RecordStruct) && ((RecordStruct) context).getFieldAsBooleanOrFalse("FromRPC"))
			return true;

		// if not in an RPC, if requested in a script, then show it
		if ((context instanceof RecordStruct) && ! ((RecordStruct) context).getFieldAsBooleanOrFalse("FromRPC"))
			return true;

		*/

		return true;
	}
}
