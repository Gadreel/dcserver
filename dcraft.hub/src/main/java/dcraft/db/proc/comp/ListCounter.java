package dcraft.db.proc.comp;

import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

import dcraft.db.DbServiceRequest;
import dcraft.db.proc.IComposer;
import dcraft.db.tables.TablesAdapter;
import dcraft.hub.ResourceHub;
import dcraft.hub.op.IVariableAware;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.time.BigDateTime;
import dcraft.schema.DbField;
import dcraft.schema.SchemaResource;
import dcraft.struct.RecordStruct;
import dcraft.struct.builder.BuilderStateException;
import dcraft.struct.builder.ICompositeBuilder;

public class ListCounter implements IComposer {
	@Override
	public void writeField(ICompositeBuilder out,
						   TablesAdapter db, IVariableAware scope, String table, String id, RecordStruct field, boolean compact) throws OperatingContextException
	{	
		try {
			String fname = field.getFieldAsString("Field");
			
			SchemaResource schema = ResourceHub.getResources().getSchema();
			DbField fdef = schema.getDbField(table, fname);

			if (fdef == null) {
				out.value(new Long(0));
				return;
			}
			
			AtomicLong cnt = new AtomicLong();
			
			if ("Id".equals(fname)) {
				cnt.set(1);
			}
			// DynamicList, StaticList (or DynamicScalar is when == null)
			// TODO was --- else if (fdef.isList() || (fdef.isDynamic() && when == null)) { --- restore?
			else if (fdef.isList()) {
				// keep in mind that `id` is the "value" in the index
				db.traverseSubIds(table, id, fname, new Function<Object,Boolean>() {
					@Override
					public Boolean apply(Object subid) {
						cnt.incrementAndGet();
						return true;
					}
				});
			}		
			// DynamicScalar
			else if (fdef.isDynamic()) {
				if (db.getDynamicScalarRaw(table, id, fname) != null)
					cnt.set(1);
			}
			// StaticScalar
			else {
				if (db.getStaticScalarRaw(table, id, fname) != null)
					cnt.set(1);
			}
			
			out.value(new Long(cnt.get()));
		} 
		catch (BuilderStateException x) {
			// TODO Auto-generated catch block
			x.printStackTrace();
		}
	}
}
