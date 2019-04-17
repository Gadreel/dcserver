package dcraft.hub.resource;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import dcraft.filestore.CommonPath;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.resource.ResourceBase;
import dcraft.hub.resource.ResourceTier;
import dcraft.script.StackUtil;
import dcraft.script.work.ReturnOption;
import dcraft.script.work.StackWork;
import dcraft.struct.ListStruct;
import dcraft.struct.Struct;
import dcraft.struct.scalar.StringStruct;
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
		this.mimeTypeMapping.put(v.getMimeType(), v);
		
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
	
	public List<MimeInfo> getDeep() {
		List<MimeInfo> list = new ArrayList<>();
		
		this.getDeep(list);
		
		return list;
	}
	
	protected void getDeep(List<MimeInfo> list) {
		MimeResource parent = this.getParentResource();
		
		if (parent != null)
			parent.getDeep(list);
		
		list.addAll(this.mimeExtMapping.values());
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
	
	@Override
	public ReturnOption operation(StackWork stack, XElement code) throws OperatingContextException {
		if ("MimeForName".equals(code.getName())) {
			String result = StackUtil.stringFromElement(stack, code, "Result");
			
			if (StringUtil.isNotEmpty(result)) {
				String extension = FileUtil.getFileExtension(StackUtil.stringFromElement(stack, code, "Name"));
				
				StackUtil.addVariable(stack, result, this.getMimeType(extension));
			}
			
			return ReturnOption.CONTINUE;
		}
		
		if ("TypeForName".equals(code.getName())) {
			String result = StackUtil.stringFromElement(stack, code, "Result");
			
			if (StringUtil.isNotEmpty(result)) {
				String extension = FileUtil.getFileExtension(StackUtil.stringFromElement(stack, code, "Name"));
				
				MimeInfo info = this.getMimeType(extension);
				
				StackUtil.addVariable(stack, result, StringStruct.of(info.getMimeType()));
			}
			
			return ReturnOption.CONTINUE;
		}
		
		if ("GetMimeDeep".equals(code.getName())) {
			String result = StackUtil.stringFromElement(stack, code, "Result");
			
			if (StringUtil.isNotEmpty(result)) {
				ListStruct list = ListStruct.list();
				List<MimeInfo> mimes = this.getDeep();
				list.withCollection(mimes);
				StackUtil.addVariable(stack, result, list);
			}
			
			return ReturnOption.CONTINUE;
		}
		
		return super.operation(stack, code);
	}
}
