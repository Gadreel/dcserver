package dcraft.cms.util;

import dcraft.filestore.CommonPath;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
import dcraft.log.Logger;
import dcraft.struct.BaseStruct;
import dcraft.struct.CompositeStruct;
import dcraft.struct.RecordStruct;
import dcraft.struct.Struct;
import dcraft.util.StringUtil;

public class GalleryUtil {
	// =========================================
	// general functions
	// =========================================

	static public RecordStruct getMeta(String path) throws OperatingContextException {
		return GalleryUtil.getMeta(path, OperationContext.getOrThrow().selectAsString("Controller.Request.View"));
	}

	static public RecordStruct getMeta(String path, String view) throws OperatingContextException {
		if (StringUtil.isEmpty(path))
			return null;

		try {
			CommonPath path1 = CommonPath.from(path);

			if (path1 == null)
				return null;

			CompositeStruct res = OperationContext.getOrThrow().getSite().getJsonResource("galleries", path1.isRoot() ? "/meta.json" : path + "/meta.json", view);

			if (res != null)
				return Struct.objectToRecord(res);

			if (path1.isRoot())
				return null;

			return GalleryUtil.getMeta(path1.getParent().toString(), view);
		}
		catch (IllegalArgumentException x) {
			Logger.warn("Path is not valid: " + x);

			return null;
		}
	}

	static public RecordStruct getImageMeta(String path) throws OperatingContextException {
		return GalleryUtil.getImageMeta(path, OperationContext.getOrThrow().selectAsString("Controller.Request.View"));
	}

	// always return something, even empty record if no file
	static public RecordStruct getImageMeta(String path, String view) throws OperatingContextException {
		if (StringUtil.isEmpty(path))
			return RecordStruct.record();

		if (! path.endsWith(".v"))
			path += ".v";

		RecordStruct meta = Struct.objectToRecord(GalleryUtil.getMeta(path, view));

		if (meta == null)
			meta = RecordStruct.record();

		// take the default data
		RecordStruct data = meta.isNotFieldEmpty("default") ? meta.getFieldAsRecord("default") : RecordStruct.record();

		// override with current locale
		data.copyFields(meta.getFieldAsRecord(OperationContext.getOrThrow().getSite().getResources().getLocale().getDefaultLocale()));

		return data;
	}

	static public void forEachGalleryShowImage(String path, String show, String view, GalleryImageConsumer consumer) throws OperatingContextException {
		GalleryUtil.forEachGalleryShowImage(GalleryUtil.getMeta(path, view), path, show, consumer);
	}
	
	static public void forEachGalleryShowImage(RecordStruct gallery, String path, String show, GalleryImageConsumer consumer) throws OperatingContextException {
		if ((gallery != null) && (gallery.hasField("Shows"))) {
			for (BaseStruct s : gallery.getFieldAsList("Shows").items()) {
				RecordStruct showrec = (RecordStruct) s;
				
				if (!show.equals(showrec.getFieldAsString("Alias")))
					continue;

				for (BaseStruct i : showrec.getFieldAsList("Images").items()) {
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

		int dpos= alias.indexOf('.');

		if (dpos > 0)
			alias = alias.substring(0, dpos);
		
		if ((meta != null) && meta.isNotFieldEmpty("Variations")) {
			for (BaseStruct vs : meta.getFieldAsList("Variations").items()) {
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
			for (BaseStruct vs : meta.getFieldAsList("Shows").items()) {
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
			for (BaseStruct vs : meta.getFieldAsList("UploadPlans").items()) {
				RecordStruct vari = (RecordStruct) vs;
				
				if (alias.equals(vari.getFieldAsString("Alias")))
					return vari;
			}
		}
		
		return null;
	};
}
