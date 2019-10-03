package dcraft.db.proc.trigger;

import dcraft.core.db.UserDataUtil;
import dcraft.db.proc.ITrigger;
import dcraft.db.proc.call.SignIn;
import dcraft.db.tables.TableUtil;
import dcraft.db.tables.TablesAdapter;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
import dcraft.struct.FieldStruct;
import dcraft.struct.RecordStruct;
import dcraft.struct.Struct;
import dcraft.util.StringUtil;

public class CheckWriteUserRecord implements ITrigger {
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

		/*
		if (context instanceof RecordStruct) {
			//RecordStruct info = (RecordStruct) context;

			//String op = info.getFieldAsString("Op");

			//if (op.startsWith("") && )

		}
		*/

		return true;
	}
}
