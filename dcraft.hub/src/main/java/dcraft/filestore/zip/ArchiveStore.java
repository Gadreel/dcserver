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

import dcraft.filestore.CommonPath;
import dcraft.filestore.FileStore;
import dcraft.filestore.FileStoreFile;
import dcraft.filestore.IFileCollection;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
import dcraft.hub.op.OperationOutcome;
import dcraft.hub.op.OperationOutcomeEmpty;
import dcraft.log.Logger;
import dcraft.script.StackUtil;
import dcraft.script.work.ExecuteState;
import dcraft.script.work.ReturnOption;
import dcraft.script.work.StackWork;
import dcraft.struct.BaseStruct;
import dcraft.struct.RecordStruct;
import dcraft.struct.ScalarStruct;
import dcraft.struct.Struct;
import dcraft.struct.scalar.NullStruct;
import dcraft.struct.scalar.StringStruct;
import dcraft.util.FileUtil;
import dcraft.util.IOUtil;
import dcraft.util.StringUtil;
import dcraft.util.io.IFileWatcher;
import dcraft.xml.XElement;
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;

public class ArchiveStore extends FileStore {

    static public ArchiveStore of(String path) throws OperatingContextException {
        ArchiveStore driver = new ArchiveStore();
        driver.localpath = Paths.get(path);
        driver.connect(null, null);
        return driver;
    }

    static public ArchiveStore of(Path path) throws OperatingContextException {
        ArchiveStore driver = new ArchiveStore();
        driver.localpath = path;
        driver.connect(null, null);
        return driver;
    }

    protected Path localpath = null;
    protected List<IFileWatcher> observers = new ArrayList<>();
    protected boolean tempfolder = false;

    public ArchiveStore() {
        this.with("Scanner", ArchiveStoreScanner.of(this));
        this.with("RootFolder", "/");
    }

    @Override
    public void connect(RecordStruct params, OperationOutcomeEmpty callback) throws OperatingContextException {
        if (callback == null)
            return;

        callback.returnEmpty();
    }

    public ZipArchiveInputStream getArchiveReadAccess() {
        if (! Files.exists(this.localpath))
            return null;

        try {
            InputStream inputStream = Files.newInputStream(this.localpath, StandardOpenOption.READ);

            if (inputStream != null)
                return new ZipArchiveInputStream(inputStream);
        }
        catch (IOException x) {
        }

        return null;
    }

    // caller must close
    public ZipArchiveOutputStream getArchiveWriteAccess() {
        try {
            Files.createDirectories(this.localpath.getParent());

            OutputStream out = Files.newOutputStream(this.localpath,
                    StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.SYNC);

            return new ZipArchiveOutputStream(out);
        }
        catch (IOException x) {
        }

        return null;
    }

    @Override
    protected void doCopy(BaseStruct n) {
        ArchiveStore cp = (ArchiveStore) n;
        cp.localpath = this.localpath;

        super.doCopy(n);
    }

    @Override
    public ArchiveStore deepCopy() {
        ArchiveStore cp = new ArchiveStore();
        this.doCopy(cp);
        return cp;
    }

    @Override
    public boolean close(OperationOutcomeEmpty callback) {
        if (this.isTemp()) {
            try {
                Files.deleteIfExists(this.localpath);
            }
            catch (IOException x) {
            }
        }

        return true;
    }

    public void isTemp(boolean v) {
        this.tempfolder = v;
    }

    public boolean isTemp() {
        return this.tempfolder;
    }

    public void withObserver(IFileWatcher v) {
        this.observers.add(v);
    }

    public void fireFolderEvent(Path fname, WatchEvent.Kind<Path> kind) {
        for (IFileWatcher watcher : this.observers)
            watcher.fireFolderEvent(fname, kind);
    }

    @Override
    public void getFileDetail(CommonPath path, OperationOutcome<FileStoreFile> callback) {
        ArchiveStoreFile f = ArchiveStoreFile.of(this, path);

        callback.returnValue(f);
    }

    public String getRootFolder() {
        return this.getFieldAsString("RootFolder");
    }

    public void setRootFolder(String path) {
        this.with("RootFolder", path);
    }

    @Override
    public void addFolder(CommonPath path, OperationOutcome<FileStoreFile> callback) {
        // essentially there are no folders so it always works?
        callback.returnValue(ArchiveStoreFile.of(this, path));
    }

    @Override
    public void removeFile(CommonPath path, OperationOutcomeEmpty callback) {
        /*
        Path localpath = this.resolvePath(path);

        FileUtil.deleteDirectory(localpath);
         */

        Logger.error("Remove file feature not currently supported");

        callback.returnEmpty();
    }

    @Override
    public void queryFeatures(OperationOutcome<RecordStruct> callback) {
        // TODO Auto-generated method stub

    }

    @Override
    public void customCommand(RecordStruct params, OperationOutcome<RecordStruct> callback) {
        // TODO Auto-generated method stub

    }

    @Override
    public IFileCollection scanner(CommonPath path) {
        return ArchiveStoreFile.of(this, path).scanner();
    }

    @Override
    public ArchiveStoreFile rootFolder() {
        return ArchiveStoreFile.of(this, CommonPath.ROOT);
    }

    @Override
    public ArchiveStoreFile fileReference(CommonPath path) {
        return ArchiveStoreFile.of(this, path);
    }

    @Override
    public ArchiveStoreFile fileReference(CommonPath path, boolean isFolder) {
        return ArchiveStoreFile.of(this, path, isFolder);
    }

    @Override
    public void getFolderListing(CommonPath path, OperationOutcome<List<FileStoreFile>> callback) {
        ArchiveStoreFile f = ArchiveStoreFile.of(this, path);

        f.getFolderListing(callback);
    }

    @Override
    public ReturnOption operation(StackWork stack, XElement code) throws OperatingContextException {
        if ("Delete".equals(code.getName())) {
            this.close(null);
            return ReturnOption.CONTINUE;
        }

        /*
		if ("Connect".equals(codeEl.getName())) {
			RecordStruct file = (RecordStruct) stack.refFromElement(codeEl, "File");
			
			this.connect(file, new OperationNoOutcome() {				
				@Override
				public void callback() {
					stack.resume();
				}
			});

			return;
		}
		
		if ("Close".equals(codeEl.getName())) {
			this.close(new OperationNoOutcome() {				
				@Override
				public void callback() {
					stack.resume();
				}
			});

			return;
		}
		*/

        /*
		if ("GetInfo".equals(codeEl.getName())) {
			String path = stack.stringFromElement(codeEl, "Path");

			if (StringUtil.isEmpty(path)) {
				// TODO log missing
				stack.resume();
				return;
			}
			
	        String handle = stack.stringFromElement(codeEl, "Handle");

			if (handle != null) 
	            stack.addVariable(handle, new ArchiveFile(ArchiveDriver.this, new RecordStruct(new FieldStruct("Path", path))));
			
			stack.resume();
			return;
		}
		
		if ("Put".equals(codeEl.getName())) {
			/* TODO
			Struct src = stack.refFromElement(codeEl, "Source");

			if (src == null) {
				// TODO log missing
				stack.resume();
				return;
			}

			if (!(src instanceof IFileStoreFile) && ! (src instanceof RecordStruct)) {
				// TODO log wrong type
				stack.resume();
				return;
			}
			
			RecordStruct rsrc = (RecordStruct)src;
			final IFileStoreFile ssrc = (IFileStoreFile)src;
			boolean relative = stack.boolFromElement(codeEl, "Relative", true);
			
			String cwd = this.getFieldAsString("RootFolder");
			
			String dfilepath = cwd + "/" + (relative ? rsrc.getFieldAsString("Path") : rsrc.getFieldAsString("Name"));
			
			System.out.println("copied to: " + dfilepath);
			
			final File dest = new File(dfilepath);
			dest.getParentFile().mkdirs();
			
			try {
				final FileOutputStream out = new FileOutputStream(dest);	-- don't use fos
				
				ssrc.copyTo(out, new OperationNoOutcome() {				
					@Override
					public void callback() {
						// TODO improve, check abort, etc
						
						try {
							out.close();
						} 
						catch (IOException x) {
						}
						
				        String handle = stack.stringFromElement(codeEl, "Handle");

						if (handle != null) 
				            stack.addVariable(handle, new ArchiveFile(ArchiveDriver.this, dest));
						
						stack.resume();
					}
				});
				
				return;
			}
			catch (Exception x) {
				// TODO
				//ssrc.abort();
			}			
			
			stack.resume();
			return;
			* /
		}

    	*/

        return super.operation(stack, code);
	}
}
