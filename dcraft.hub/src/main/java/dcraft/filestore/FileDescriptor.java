package dcraft.filestore;

import dcraft.struct.RecordStruct;
import dcraft.struct.Struct;

import java.time.ZonedDateTime;

public class FileDescriptor extends RecordStruct {
	static public FileDescriptor of(String path) {
		FileDescriptor fd = new FileDescriptor();
		fd.withPath(path);
		return fd;
	}
	
	public String getName() {
		return this.getPathAsCommon().getFileName();
	}
	
	public String getPath() {
		return this.getFieldAsString("Path");
	}
	
	public CommonPath getPathAsCommon() {
		return new CommonPath(this.getFieldAsString("Path"));
	}
	
	public FileDescriptor withPath(String v) {
		this.with("Path", v);
		return this;
	}
	
	public FileDescriptor withPath(CommonPath v) {
		this.with("Path", v.toString());
		return this;
	}
	
	public ZonedDateTime getModificationAsTime() {
		return this.getFieldAsDateTime("Modified");
	}
	
	public String getModification() {
		return this.getFieldAsString("Modified");
	}
	
	public FileDescriptor withModificationTime(ZonedDateTime v) {
		this.with("Modified", v);
		return this;
	}
	
	public long getSize() {
		return this.getFieldAsInteger("Size", 0);
	}
	
	public FileDescriptor withSize(long v) {
		this.with("Size", v);
		return this;
	}
	
	public boolean isFolder() {
		return this.getFieldAsBooleanOrFalse("IsFolder");
	}
	
	public FileDescriptor withIsFolder(boolean v) {
		this.with("IsFolder", v);
		return this;
	}
	
	public CommonPath resolvePath(CommonPath path) {
		if (this.isFolder())
			return this.getPathAsCommon().resolve(path);
		
		return this.getPathAsCommon().getParent().resolve(path);
	}
	
	@Override
	public FileDescriptor deepCopy() {
		FileDescriptor cp = new FileDescriptor();
		this.doCopy(cp);
		return cp;
	}
	
	@Override
	public boolean validate() {
		if (! this.hasExplicitType())
			return super.validate("dcFileDescriptor");
		
		return super.validate();
	}
}
	
