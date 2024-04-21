package dcraft.cms.store.db.comp;

import dcraft.db.proc.IComposer;
import dcraft.db.tables.TablesAdapter;
import dcraft.hub.op.IVariableAware;
import dcraft.hub.op.OperatingContextException;
import dcraft.struct.RecordStruct;
import dcraft.struct.Struct;
import dcraft.struct.builder.BuilderStateException;
import dcraft.struct.builder.ICompositeBuilder;
import dcraft.util.StringUtil;

import java.math.BigDecimal;
import java.text.DecimalFormat;

public class ProductPrice implements IComposer {
	@Override
	public void writeField(ICompositeBuilder out, TablesAdapter db, IVariableAware scope, String table, String id,
						   RecordStruct params, boolean compact) throws OperatingContextException
	{	
		try {
			BigDecimal price = Struct.objectToDecimal(db.getScalar(table, id, "dcmSalePrice"));

			if (price == null)
				price = Struct.objectToDecimal(db.getScalar(table, id, "dcmPrice"));

			if (price == null)
				price = BigDecimal.ZERO;

			String format = "Full";

			if (StringUtil.isNotEmpty(params.selectAsString("Params.Format")))
				format = params.selectAsString("Params.Format");

			if ("Basic".equals(format)) {

				out.value(price);

			} else {
				out.value(new DecimalFormat("#,###.00").format(price));
			}
		}
		catch (BuilderStateException x) {
			// TODO Auto-generated catch block
			x.printStackTrace();
		}
	}
}
