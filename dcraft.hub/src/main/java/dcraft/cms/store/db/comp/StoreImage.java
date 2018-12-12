package dcraft.cms.store.db.comp;

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
			String image = Struct.objectToString(db.getStaticScalar(table, id, "dcmImage"));
			String alias = Struct.objectToString(db.getStaticScalar(table, id, "dcmAlias"));
			
			if (StringUtil.isEmpty(image))
				image = "main";
				
			String area = "dcmCategory".equals(table) ? "category" : "product";
			
			String imagePath = "/store/" + area + "/" + alias + "/" + image;
			
			// there should always be a "thumb" so check for it
			
			LocalStoreFile file = (LocalStoreFile) OperationContext.getOrThrow().getSite().getGalleriesVault()
					.getFileStore().fileReference(CommonPath.from(imagePath + ".v/thumb.jpg"));
			
			if (! file.exists())
				imagePath = "/store/" + area + "/not-found";
				
			out.value(imagePath);
		}
		catch (BuilderStateException x) {
			// TODO Auto-generated catch block
			x.printStackTrace();
		}
	}
}
