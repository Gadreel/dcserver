package dcraft.db.proc.comp;

import dcraft.db.DatabaseException;
import dcraft.db.proc.IComposer;
import dcraft.db.tables.TablesAdapter;
import dcraft.hub.ResourceHub;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
import dcraft.log.Logger;
import dcraft.schema.DbField;
import dcraft.schema.SchemaResource;
import dcraft.struct.ListStruct;
import dcraft.struct.RecordStruct;
import dcraft.struct.Struct;
import dcraft.struct.builder.BuilderStateException;
import dcraft.struct.builder.ICompositeBuilder;
import dcraft.util.StringUtil;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

public class Translate implements IComposer {
	@Override
	public void writeField(ICompositeBuilder out, TablesAdapter db, String table, String id,
						   RecordStruct field, boolean compact) throws OperatingContextException
	{	
		try {
			String fname = field.getFieldAsString("Field");
			
			SchemaResource schema = ResourceHub.getResources().getSchema();
			DbField schemafld = schema.getDbField(table, fname + "Tr");
			
			if (schemafld != null) {
				Object val = db.getStaticList(table, id, fname + "Tr", OperationContext.getOrThrow().getLocale(), field.getFieldAsString("Format"));
				
				if (val != null) {
					out.value(val);
					return;
				}
			}
		
			out.value(db.getStaticScalar(table, id, fname, field.getFieldAsString("Format")));
		} 
		catch (BuilderStateException x) {
			// TODO Auto-generated catch block
			x.printStackTrace();
		}
	}
}
