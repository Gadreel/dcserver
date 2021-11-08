package dcraft.filestore;

import dcraft.hub.op.OperatingContextException;
import dcraft.log.Logger;
import dcraft.struct.*;
import dcraft.struct.scalar.StringStruct;

import java.time.ZonedDateTime;
import java.util.Arrays;

public class FileDescriptor extends RecordStruct {
	// TODO move this, confusing for subclasses
	static public FileDescriptor of(String path) {
		FileDescriptor fd = new FileDescriptor();
		fd.withPath(path);
		return fd;
	}
	
	public boolean exists() {
		return this.getFieldAsBooleanOrFalse("Exists");
	}
	
	public FileDescriptor withExists(boolean v) {
		this.with("Exists", v);
		return this;
	}
	
	// file description has been confirmed by consulting the source of the file
	public boolean confirmed() {
		return this.getFieldAsBooleanOrFalse("Confirmed");
	}
	
	public FileDescriptor withConfirmed(boolean v) {
		this.with("Confirmed", v);
		return this;
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
	
	public RecordStruct getExtra() throws OperatingContextException {
		return this.getFieldAsRecord("Extra");
	}
	
	public CommonPath resolvePath(CommonPath path) {
		if (this.isFolder())
			return this.getPathAsCommon().resolve(path);
		
		return this.getPathAsCommon().getParent().resolve(path);
	}

	@Override
	public BaseStruct select(PathPart... path) {
		if (path.length == 1) {
			PathPart part = path[0];

			if (part.isField() && "Name".equals(part.getField())) {
				return StringStruct.of(this.getName());
			}
		}

		return super.select(path);
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
	
