package dcraft.util;

import dcraft.struct.RecordStruct;

public class MimeInfo extends RecordStruct {
	//public static String octetStream() {
	//	return "application/octetstream";
	//}

	// don't mutate this
	public static MimeInfo DEFAULT = new MimeInfo().withCompress(false).withExt("txt").withIcon("far/file").withMimeType("text/plain");
	
	public static MimeInfo create() {
		return new MimeInfo();
	}
	
	public String getExt() {
		return this.getFieldAsString("Ext");
	}
	
	public MimeInfo withExt(String v) {
		this.with("Ext", v);
		return this;
	}
	
	public String getMimeType() {
		return this.getFieldAsString("Type");
	}
	
	public MimeInfo withMimeType(String v) {
		this.with("Type", v);
		return this;
	}
	
	public String getIcon() {
		return this.getFieldAsString("Icon");
	}
	
	public MimeInfo withIcon(String v) {
		this.with("Icon", v);
		return this;
	}
	
	public boolean isCompress() {
		return this.getFieldAsBooleanOrFalse("Compress");
	}
	
	public MimeInfo withCompress(boolean v) {
		this.with("Compress", v);
		return this;
	}
}