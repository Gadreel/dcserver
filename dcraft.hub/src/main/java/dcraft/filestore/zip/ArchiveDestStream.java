/* ************************************************************************
#
#  designCraft.io
#
#  http://designcraft.io/
#
#  Copyright:
#    Copyright 2014 eTimeline, LLC. All rights reserved.
#
#  License:
#    See the license.txt file in the project's top-level directory for details.
#
#  Authors:
#    * Andy White
#
************************************************************************ */
package dcraft.filestore.zip;

import dcraft.filestore.FileStore;
import dcraft.filestore.FileStoreFile;
import dcraft.filestore.local.LocalStoreFile;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
import dcraft.log.Logger;
import dcraft.script.StackUtil;
import dcraft.stream.ReturnOption;
import dcraft.stream.StreamUtil;
import dcraft.stream.file.BaseFileStream;
import dcraft.stream.file.FileSlice;
import dcraft.stream.file.IFileStreamDest;
import dcraft.struct.BaseStruct;
import dcraft.struct.Struct;
import dcraft.struct.scalar.NullStruct;
import dcraft.task.IParentAwareWork;
import dcraft.util.FileUtil;
import dcraft.util.IOUtil;
import dcraft.xml.XElement;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.StandardWatchEventKinds;

public class ArchiveDestStream extends BaseFileStream implements IFileStreamDest {
	static public ArchiveDestStream from(ArchiveStoreFile file) {
		ArchiveDestStream fds = new ArchiveDestStream();
		fds.currfile = file;
		fds.file = file;
		return fds;
	}
	
	protected ArchiveStoreFile file = null;
	protected ZipArchiveOutputStream out = null;
	protected boolean userelpath = true;
	protected ZipArchiveEntry currentry = null;

	protected ArchiveDestStream() {
	}

	public ArchiveDestStream withRelative(boolean v) {
		this.userelpath = v;
		return this;
	}
	
	// for use with dcScript
	@Override
	public void init(IParentAwareWork stack, XElement el) throws OperatingContextException {
			// TODO autorelative and re-think RelativeTo, shouldn't a stream always come from a file so this is redundant?
		if (StackUtil.boolFromElement(stack, el, "Relative", true) || el.getName().startsWith("X")) {
        	this.userelpath = true;
        }

		BaseStruct src = StackUtil.refFromElement(stack, el, "RelativeTo");
        
        if ((src != null) && !(src instanceof NullStruct)) {
            if (src instanceof FileStore)
            	this.currfile = (ArchiveStoreFile) ((FileStore) src).rootFolder();
            else if (src instanceof FileStoreFile)
            	this.currfile = ((ArchiveStoreFile) src);
            
        	this.userelpath = true;
        }
	}
	
	@Override
	public void close() throws OperatingContextException {
		//System.out.println("File DEST killed");	// TODO
		
		if (this.out != null)
			try {
				this.out.flush();
				this.out.close();
			} 
			catch (IOException x) {
			}
		
		this.out = null;
		
		super.close();
	}
	
	// TODO someday support Append and Resume type features
	
	@Override
	public ReturnOption handle(FileSlice slice) throws OperatingContextException {
		if (slice == FileSlice.FINAL) {
			// cleanup here because although we call task complete below, and task complete
			// also does cleanup, if we aer in a work chain that cleanup may not fire for a
			// while. This is the quicker way to let go of resources - but task end will also
			try {
				this.cleanup();
			}
			catch (Exception x) {
				Logger.warn("Stream cleanup did produced errors: " + x);
			}
			
			OperationContext.getAsTaskOrThrow().returnEmpty();
			return ReturnOption.DONE;
		}

		if (this.out == null) {
			this.out = this.file.getDriver().getArchiveWriteAccess();

			if (this.out == null) {
				slice.release();

				OperationContext.getAsTaskOrThrow().kill("Problem opening destination file: ");
				return ReturnOption.DONE;
			}
		}

		try {
			if (this.currentry == null) {
				this.currentry = new ZipArchiveEntry(slice.getFile().getPath().substring(1).replace('/', '\\')
						+ (slice.getFile().isFolder() ? "\\" : ""));
				this.currentry.setSize(slice.getFile().getSize());
				this.out.putArchiveEntry(this.currentry);

				//System.out.println("added: " + this.currentry);
			}

			if (slice.getData() != null) {
				//System.out.println("writing: " + FileUtil.formatFileSize(slice.getData().writerIndex()));

				this.out.write(slice.getData().array(), slice.getData().arrayOffset(), slice.getData().writerIndex());
				slice.release();
			}

			if (slice.isEof()) {
				this.out.closeArchiveEntry();

				//System.out.println("closed: " + this.currentry);

				this.currentry = null;
			}
		}
		catch (IOException x) {
			slice.release();
			OperationContext.getAsTaskOrThrow().kill("Problem adding folder: " + x);
			return ReturnOption.DONE;
		}

		return ReturnOption.CONTINUE;
	}

	@Override
	public void execute() throws OperatingContextException {
		// TODO optimize if upstream is local file also
		
		this.upstream.read();
	}
}
