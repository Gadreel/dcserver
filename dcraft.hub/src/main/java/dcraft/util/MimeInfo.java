package dcraft.util;

public class MimeInfo {
	//public static String octetStream() {
	//	return "application/octetstream";
	//}
	
	public static MimeInfo DEFAULT = new MimeInfo().withCompress(false).withExt("bin").withType("application/octetstream");
	
	public static MimeInfo create() {
		return new MimeInfo();
	}
	
	protected String ext = null;
	protected String type = null;
	protected boolean compress = false;
	
	public String getExt() {
		return this.ext;
	}
	
	public MimeInfo withExt(String v) {
		this.ext = v;
		return this;
	}
	
	public String getType() {
		return this.type;
	}
	
	public MimeInfo withType(String v) {
		this.type = v;
		return this;
	}
	
	public boolean isCompress() {
		return this.compress;
	}
	
	public MimeInfo withCompress(boolean v) {
		this.compress = v;
		return this;
	}
}