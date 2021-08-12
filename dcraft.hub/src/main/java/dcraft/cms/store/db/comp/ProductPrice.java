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

public class ProductPrice implements IComposer {
	@Override
	public void writeField(ICompositeBuilder out, TablesAdapter db, IVariableAware scope, String table, String id,
						   RecordStruct field, boolean compact) throws OperatingContextException
	{	
		try {
			BigDecimal price = Struct.objectToDecimal(db.getScalar(table, id, "dcmSalePrice"));

			if (price == null)
				price = Struct.objectToDecimal(db.getScalar(table, id, "dcmPrice"));

			if (price != null)
				out.value(new DecimalFormat("#,###.00").format(price));
			else
				out.value("0.00");
		}
		catch (BuilderStateException x) {
			// TODO Auto-generated catch block
			x.printStackTrace();
		}
	}
}
