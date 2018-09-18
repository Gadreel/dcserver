package dcraft.db.proc.comp;

import dcraft.db.proc.IComposer;
import dcraft.db.tables.TablesAdapter;
import dcraft.hub.op.IVariableAware;
import dcraft.hub.op.OperatingContextException;
import dcraft.struct.RecordStruct;
import dcraft.struct.Struct;
import dcraft.struct.builder.BuilderStateException;
import dcraft.struct.builder.ICompositeBuilder;
import dcraft.util.RndUtil;

import java.math.BigDecimal;
import java.text.DecimalFormat;

public class TermScore implements IComposer {
	@Override
	public void writeField(ICompositeBuilder out, TablesAdapter db, IVariableAware scope, String table, String id,
						   RecordStruct field, boolean compact) throws OperatingContextException
	{
		try {
			if (scope != null) {
				RecordStruct rcache = (RecordStruct) scope.queryVariable("_RecordCache");

				if (rcache != null) {
					// TODO make so init can decide the fld name in case multiple terms are being used
					out.value(rcache.getFieldAsDecimal("TermScore"));
				}
				else {
					out.value(BigDecimal.ZERO);
				}
			}
		}
		catch (BuilderStateException x) {
			// TODO Auto-generated catch block
			x.printStackTrace();
		}
	}
}
