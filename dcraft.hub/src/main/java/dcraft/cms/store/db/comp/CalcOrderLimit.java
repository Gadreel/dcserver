package dcraft.cms.store.db.comp;

import dcraft.db.proc.IComposer;
import dcraft.db.tables.TablesAdapter;
import dcraft.hub.op.IVariableAware;
import dcraft.hub.op.OperatingContextException;
import dcraft.struct.RecordStruct;
import dcraft.struct.Struct;
import dcraft.struct.builder.BuilderStateException;
import dcraft.struct.builder.ICompositeBuilder;

public class CalcOrderLimit implements IComposer {
	@Override
	public void writeField(ICompositeBuilder out, TablesAdapter db, IVariableAware scope, String table, String id,
						   RecordStruct field, boolean compact) throws OperatingContextException
	{	
		try {
			Long limit = lookup(db, table, id);

			out.value(limit);
		}
		catch (BuilderStateException x) {
			// TODO Auto-generated catch block
			x.printStackTrace();
		}
	}

	static public Long lookup(TablesAdapter db, String table, String id) throws OperatingContextException {
		boolean show = Struct.objectToBoolean(db.getScalar(table, id, "dcmShowInStore"), false);

		if (! show)
			return 0L;

		Long limit = Struct.objectToInteger(db.getScalar(table, id, "dcmOrderLimit"));
		Long inventory = Struct.objectToInteger(db.getScalar(table, id, "dcmInventory"));

		if (inventory != null)  {
			if (limit == null)
				limit = inventory;
			else if (inventory < limit)
				limit = inventory;
		}

		return limit;
	}
}
