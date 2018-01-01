package dcraft.db.proc.comp;

import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

import dcraft.db.DbServiceRequest;
import dcraft.db.proc.IComposer;
import dcraft.db.tables.TablesAdapter;
import dcraft.hub.ResourceHub;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.time.BigDateTime;
import dcraft.schema.DbField;
import dcraft.schema.SchemaResource;
import dcraft.struct.RecordStruct;
import dcraft.struct.Struct;
import dcraft.struct.builder.BuilderStateException;
import dcraft.struct.builder.ICompositeBuilder;
import dcraft.util.StringUtil;

// TODO re-think, this is not yet used
public class IndexValueCounter implements IComposer {
	@Override
	public void writeField(DbServiceRequest task, ICompositeBuilder out,
						   TablesAdapter db, String table, String id, BigDateTime when, RecordStruct field,
						   boolean historical, boolean compact) throws OperatingContextException
	{	
		try {
			String fname = field.getFieldAsString("Field");

			if (StringUtil.isEmpty(fname)) {
				out.value(new Long(0));
				return;
			}
			
			SchemaResource schema = ResourceHub.getResources().getSchema();
			DbField fdef = schema.getDbField(table, fname);

			if (fdef == null) {
				out.value(new Long(0));
				return;
			}
			
			RecordStruct params = field.getFieldAsRecord("Params");

			if ((params == null) || params.isFieldEmpty("Value")) {
				out.value(new Long(0));
				return;
			}
			
			// get as a type we understand
			Object val = Struct.objectToCore(field.getField("Value"));
			
			AtomicLong cnt = new AtomicLong();

			db.traverseIndex(table, fname, val, when, historical, new Function<Object,Boolean>() {				
				@Override
				public Boolean apply(Object subid) {
					cnt.incrementAndGet();
					return true;
				}
			});
			
			out.value(new Long(cnt.get()));
		} 
		catch (BuilderStateException x) {
			// TODO Auto-generated catch block
			x.printStackTrace();
		}
	}
}
