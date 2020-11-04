package dcraft.cms.store.db.comp;

import dcraft.db.proc.IComposer;
import dcraft.db.tables.TablesAdapter;
import dcraft.hub.op.IVariableAware;
import dcraft.hub.op.OperatingContextException;
import dcraft.struct.RecordStruct;
import dcraft.struct.Struct;
import dcraft.struct.builder.BuilderStateException;
import dcraft.struct.builder.ICompositeBuilder;

import java.math.BigDecimal;
import java.text.DecimalFormat;

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
		Long limit = Struct.objectToInteger(db.getStaticScalar(table, id, "dcmOrderLimit"));
		Long inventory = Struct.objectToInteger(db.getStaticScalar(table, id, "dcmInventory"));

		if (inventory != null)  {
			if (limit == null)
				limit = inventory;
			else if (inventory < limit)
				limit = inventory;
		}

		return limit;
	}
}
