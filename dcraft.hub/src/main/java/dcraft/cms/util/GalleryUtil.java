package dcraft.cms.util;

import dcraft.filestore.CommonPath;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
import dcraft.struct.CompositeStruct;
import dcraft.struct.RecordStruct;
import dcraft.struct.Struct;
import dcraft.util.StringUtil;

public class GalleryUtil {
	// =========================================
	// general functions
	// =========================================
	
	static public CompositeStruct getMeta(String path, String view) throws OperatingContextException {
		CommonPath path1 = CommonPath.from(path);
		
		CompositeStruct res = OperationContext.getOrThrow().getSite().getJsonResource("galleries", path1.isRoot() ? "/meta.json" : path + "/meta.json", view);

		if (res != null)
			return res;
		
		if (path1.isRoot())
			return null;
		
		return GalleryUtil.getMeta(path1.getParent().toString(), view);
	}
	
	static public void forEachGalleryShowImage(String path, String show, String view, GalleryImageConsumer consumer) throws OperatingContextException {
		GalleryUtil.forEachGalleryShowImage((RecordStruct) GalleryUtil.getMeta(path, view), path, show, consumer);
	}
	
	static public void forEachGalleryShowImage(RecordStruct gallery, String path, String show, GalleryImageConsumer consumer) throws OperatingContextException {
		if ((gallery != null) && (gallery.hasField("Shows"))) {
			for (Struct s : gallery.getFieldAsList("Shows").items()) {
				RecordStruct showrec = (RecordStruct) s;
				
				if (!show.equals(showrec.getFieldAsString("Alias")))
					continue;
				
				for (Struct i : showrec.getFieldAsList("Images").items()) {
					String img = i.toString();
					
					consumer.accept(gallery, showrec, RecordStruct.record()
							.with("Alias", img)
							.with("Path", path + "/" + img)
					);
				}
			}
		}
	}

	// =========================================
	// specific gallery meta.json functions
	// =========================================
	
	static public RecordStruct findVariation(RecordStruct meta, String alias) {
		if (StringUtil.isEmpty(alias))
			return null;
		
		if ((meta != null) && meta.isNotFieldEmpty("Variations")) {
			for (Struct vs : meta.getFieldAsList("Variations").items()) {
				RecordStruct vari = (RecordStruct) vs;
				
				if (alias.equals(vari.getFieldAsString("Alias")))
					return vari;
			}
		}
		
		return null;
	};

	static public RecordStruct findShow(RecordStruct meta, String alias) {
		if (StringUtil.isEmpty(alias))
			return null;
		
		if ((meta != null) && meta.isNotFieldEmpty("Shows")) {
			for (Struct vs : meta.getFieldAsList("Shows").items()) {
				RecordStruct vari = (RecordStruct) vs;
				
				if (alias.equals(vari.getFieldAsString("Alias")))
					return vari;
			}
		}
		
		return null;
	};

	static public RecordStruct findPlan(RecordStruct meta, String alias) {
		if (StringUtil.isEmpty(alias))
			return null;
		
		if ((meta != null) && meta.isNotFieldEmpty("UploadPlans")) {
			for (Struct vs : meta.getFieldAsList("UploadPlans").items()) {
				RecordStruct vari = (RecordStruct) vs;
				
				if (alias.equals(vari.getFieldAsString("Alias")))
					return vari;
			}
		}
		
		return null;
	};
}
