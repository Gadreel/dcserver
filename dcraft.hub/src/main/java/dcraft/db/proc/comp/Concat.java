package dcraft.db.proc.comp;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import dcraft.db.proc.IComposer;
import dcraft.db.tables.TablesAdapter;
import dcraft.hub.ResourceHub;
import dcraft.hub.op.IVariableAware;
import dcraft.hub.op.OperatingContextException;
import dcraft.log.Logger;
import dcraft.schema.DbField;
import dcraft.schema.SchemaResource;
import dcraft.struct.ListStruct;
import dcraft.struct.RecordStruct;
import dcraft.struct.Struct;
import dcraft.struct.builder.ICompositeBuilder;
import dcraft.util.StringUtil;

public class Concat implements IComposer {
	@Override
	public void writeField(ICompositeBuilder out, TablesAdapter db, IVariableAware scope, String table, String id,
						   RecordStruct field, boolean compact) throws OperatingContextException
	{	
		try {
			RecordStruct params = field.getFieldAsRecord("Params");
			
			ListStruct items = params.getFieldAsList("Parts");
			String ret = "";
			
			if (items != null) {
				//LoadRecord lr  = new LoadRecord();
				
				for (int i = 0; i < items.size(); i++) {
					RecordStruct fld = (RecordStruct) items.getAt(i);
					
					ret += this.getField(db, table, id, fld, compact);
				}
			}
		
			out.value(ret);
		} 
		catch (Exception x) {
			// TODO Auto-generated catch block
			x.printStackTrace();
		}
	}
	
	public String getField(TablesAdapter db, String table, String id, RecordStruct field, boolean compact) throws Exception
	{		
		// composer not valid inside of concat
		if (!field.isFieldEmpty("Composer")) 
			return "";
		
		if (field.hasField("Value")) 
			return field.getFieldAsString("Value");
		
		String fname = field.getFieldAsString("Field");
		String format = field.getFieldAsString("Format");
		
		SchemaResource schema = ResourceHub.getResources().getSchema();
		DbField fdef = schema.getDbField(table, fname);

		if (fdef == null) 
			return "";
		
		// subquery/foreign field not allowed in concat - TODO add support for single foreign field
		
		if ("Id".equals(fname)) 
			return id;
		
		String subid = field.getFieldAsString("SubId");

		if (StringUtil.isNotEmpty(subid) && fdef.isList()) 
			return Struct.objectToString(db.getList(table, id, fname, subid, format));

		// List
		if (fdef.isList()) {
			AtomicReference<String> res = new AtomicReference<>("");
			
			// keep in mind that `id` is the "value" in the index
			db.traverseSubIds(table, id, fname, new Function<Object,Boolean>() {
				@Override
				public Boolean apply(Object subid) {
					try {
						// don't output null values in this list - Extended might return null data but otherwise no nulls
							Object value = db.getList(table, id, fname, subid.toString());
							
							if (value != null) {
								String v = res.get();
								
								if (v.length() > 0)
									v += ",";
								
								v += Struct.objectToString(value);
								
								res.set(v);
								
								return true;
							}
					}
					catch (Exception x) {
						Logger.error("Unable to write subid: " + x);
					}
					
					return false;
				}
			});
		
			return res.get();
		}		

		// Scalar
		return Struct.objectToString(db.getScalar(table, id, fname, format));
	}
}
