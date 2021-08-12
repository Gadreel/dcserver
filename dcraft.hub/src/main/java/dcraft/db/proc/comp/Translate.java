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

public class Translate implements IComposer {
	@Override
	public void writeField(ICompositeBuilder out, TablesAdapter db, IVariableAware scope, String table, String id,
						   RecordStruct field, boolean compact) throws OperatingContextException
	{	
		try {
			String fname = field.getFieldAsString("Field");
			
			SchemaResource schema = ResourceHub.getResources().getSchema();
			DbField schemafld = schema.getDbField(table, fname + "Tr");
			
			if (schemafld != null) {
				Object val = db.getList(table, id, fname + "Tr", OperationContext.getOrThrow().getLocale(), field.getFieldAsString("Format"));
				
				if (val != null) {
					out.value(val);
					return;
				}
			}
		
			out.value(db.getScalar(table, id, fname, field.getFieldAsString("Format")));
		} 
		catch (BuilderStateException x) {
			// TODO Auto-generated catch block
			x.printStackTrace();
		}
	}
}
