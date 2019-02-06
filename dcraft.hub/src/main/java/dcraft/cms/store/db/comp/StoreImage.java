package dcraft.cms.store.db.comp;

import dcraft.cms.store.db.Util;
import dcraft.db.proc.IComposer;
import dcraft.db.tables.TablesAdapter;
import dcraft.filestore.CommonPath;
import dcraft.filestore.local.LocalStoreFile;
import dcraft.hub.op.IVariableAware;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
import dcraft.struct.RecordStruct;
import dcraft.struct.Struct;
import dcraft.struct.builder.BuilderStateException;
import dcraft.struct.builder.ICompositeBuilder;
import dcraft.util.StringUtil;

import java.util.List;

public class StoreImage implements IComposer {
	@Override
	public void writeField(ICompositeBuilder out, TablesAdapter db, IVariableAware scope, String table, String id,
						   RecordStruct field, boolean compact) throws OperatingContextException
	{	
		try {
			String imagePath = Util.storeImagePath(db, table, id);
			
			out.value(imagePath);
		}
		catch (BuilderStateException x) {
			// TODO Auto-generated catch block
			x.printStackTrace();
		}
	}
}
