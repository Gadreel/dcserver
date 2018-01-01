package dcraft.hub.resource;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import dcraft.filestore.CommonPath;
import dcraft.hub.resource.ResourceBase;
import dcraft.hub.resource.ResourceTier;
import dcraft.struct.Struct;
import dcraft.util.FileUtil;
import dcraft.util.MimeInfo;
import dcraft.util.StringUtil;
import dcraft.xml.XElement;

public class MimeResource extends ResourceBase {
	protected Map<String,MimeInfo> mimeExtMapping = new HashMap<>();
	protected Map<String,MimeInfo> mimeTypeMapping = new HashMap<>();
	
	public MimeResource() {
		this.setName("Mime");
	}
	
	public MimeResource getParentResource() {
		if (this.tier == null)
			return null;
		
		ResourceTier pt = this.tier.getParent();
		
		if (pt != null)
			return pt.getMime();
		
		return null;
	}
	
	public MimeResource with(MimeInfo v) {
		this.mimeExtMapping.put(v.getExt(), v);
		this.mimeTypeMapping.put(v.getType(), v);
		
		return this;
	}
	
	public MimeInfo getMimeType(String ext) {
		if (ext == null)
			return MimeInfo.DEFAULT;
		
		ext = ext.toLowerCase();
		
		MimeInfo mt = this.mimeExtMapping.get(ext);
		
		if (mt != null)
			return mt;
		
		MimeResource parent = this.getParentResource();

		if (parent != null)
			return parent.getMimeType(ext);
		
		return MimeInfo.DEFAULT;
	}
	
	public MimeInfo getMimeTypeForPath(CommonPath path) {
		return this.getMimeType(path.getFileExtension());
	}
	
	public MimeInfo getMimeTypeForPath(Path path) {
		String ext = FileUtil.getFileExtension(path);
		
		return this.getMimeType(ext);
	}
	
	public MimeInfo getMimeTypeForName(String fname) {
		String ext = FileUtil.getFileExtension(fname);
		
		return this.getMimeType(ext);
	}

	/*
	public boolean getMimeCompress(String ext) {
		if (ext == null)
			return false;
		
		ext = ext.toLowerCase();
		
		MimeInfo mt = this.mimeExtMapping.get(ext);
		
		if (mt == null)
			mt = this.mimeTypeMapping.get(ext);
		
		if (mt != null)
			return mt.isCompress();
		
		MimeResource parent = this.getParentResource();

		if (parent != null)
			return parent.getMimeCompress(ext);
		
		return false;
	}
	*/
}
