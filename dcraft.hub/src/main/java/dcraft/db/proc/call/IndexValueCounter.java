package dcraft.db.proc.call;

import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

import dcraft.db.DbServiceRequest;
import dcraft.db.ICallContext;
import dcraft.db.proc.IComposer;
import dcraft.db.proc.IStoredProc;
import dcraft.db.proc.filter.Max;
import dcraft.db.tables.TableUtil;
import dcraft.db.tables.TablesAdapter;
import dcraft.db.util.ByteUtil;
import dcraft.hub.ResourceHub;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationOutcomeStruct;
import dcraft.hub.time.BigDateTime;
import dcraft.log.Logger;
import dcraft.schema.DataType;
import dcraft.schema.DbField;
import dcraft.schema.SchemaResource;
import dcraft.struct.ListStruct;
import dcraft.struct.RecordStruct;
import dcraft.struct.Struct;
import dcraft.struct.builder.BuilderStateException;
import dcraft.struct.builder.ICompositeBuilder;
import dcraft.util.StringUtil;

public class IndexValueCounter implements IStoredProc {
	@Override
	public void execute(ICallContext request, OperationOutcomeStruct callback) throws OperatingContextException {
		ListStruct list = ListStruct.list();
		
		try {
			RecordStruct data = request.getDataAsRecord();
			
			TablesAdapter db = TablesAdapter.ofNow(request);
			
			String table = data.getFieldAsString("Table");
			String fname = data.getFieldAsString("Field");
			ListStruct values = data.getFieldAsList("Values");
			
			if (StringUtil.isNotEmpty(fname)) {
				SchemaResource schema = ResourceHub.getResources().getSchema();
				DbField ffdef = schema.getDbField(table, fname);
				
				if (ffdef != null) {
					DataType dtype = schema.getType(ffdef.getTypeId());
					
					if (dtype != null) {
						if (values == null) {
							byte[] value = request.getInterface().nextPeerKey(request.getTenant(), ffdef.getIndexName(), table, fname, null);
							
							while (value != null) {
								Object val = ByteUtil.extractValue(value);
								
								Long count = request.getInterface().getAsInteger(request.getTenant(), ffdef.getIndexName(), table, fname, val);
								
								list.with(RecordStruct.record()
										.with("Value", val)
										.with("Count", count)
								);
								
								value = request.getInterface().nextPeerKey(request.getTenant(), ffdef.getIndexName(), table, fname, val);
							}
						} else {
							for (int i = 0; i < values.size(); i++) {
								String value = values.getItemAsString(i);
								
								// we need to input index ready values or else this function won't be like the one above
								//Object val = dtype.toIndex(value, "eng");    // TODO increase locale support
								
								Long count = request.getInterface().getAsInteger(request.getTenant(), ffdef.getIndexName(), table, fname, value);
								
								list.with(RecordStruct.record()
										.with("Value", value)
										.with("Count", count)
								);
							}
						}
					}
				}
			}
			
			callback.returnValue(list);
		}
		catch (Exception x) {
			Logger.error("Echo: Unable to create response: " + x);
			
			callback.returnEmpty();
		}
	}
}
