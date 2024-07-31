package dcraft.db.proc.comp;

import dcraft.db.proc.IComposer;
import dcraft.db.tables.TablesAdapter;
import dcraft.hub.ResourceHub;
import dcraft.hub.op.IVariableAware;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
import dcraft.schema.DbField;
import dcraft.schema.SchemaResource;
import dcraft.struct.RecordStruct;
import dcraft.struct.builder.BuilderStateException;
import dcraft.struct.builder.ICompositeBuilder;

public class SubId implements IComposer {
	@Override
	public void writeField(ICompositeBuilder out, TablesAdapter db, IVariableAware scope, String table, String id,
						   RecordStruct field, boolean compact) throws OperatingContextException
	{	
		try {
			String subid = field.getFieldAsString("SubId");

			out.value(subid);
		} 
		catch (BuilderStateException x) {
			// TODO Auto-generated catch block
			x.printStackTrace();
		}
	}
}
