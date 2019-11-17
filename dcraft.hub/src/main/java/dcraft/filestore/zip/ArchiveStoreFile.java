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

import dcraft.filestore.CollectionSourceStream;
import dcraft.filestore.CommonPath;
import dcraft.filestore.FileStoreFile;
import dcraft.filestore.IFileCollection;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationOutcome;
import dcraft.hub.op.OperationOutcomeEmpty;
import dcraft.log.Logger;
import dcraft.schema.SchemaHub;
import dcraft.script.work.ReturnOption;
import dcraft.script.work.StackWork;
import dcraft.stream.StreamFragment;
import dcraft.struct.Struct;
import dcraft.util.*;
import dcraft.util.io.InputWrapper;
import dcraft.xml.XElement;
import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class ArchiveStoreFile extends FileStoreFile {
    static public ArchiveStoreFile of(ArchiveStore driver, CommonPath path) {
        ArchiveStoreFile archiveFile = new ArchiveStoreFile();
        archiveFile.driver = driver;
        archiveFile.localdriver = driver;
        archiveFile.localpath = path;

        archiveFile.refreshProps();

        return archiveFile;
    }

    static public ArchiveStoreFile of(ArchiveStore driver, ArchiveEntry entry) {
        ArchiveStoreFile archiveFile = new ArchiveStoreFile();
        archiveFile.driver = driver;
        archiveFile.localdriver = driver;
        archiveFile.entry = entry;
        archiveFile.localpath = CommonPath.from("/" + entry.getName());

        archiveFile.refreshProps();

        return archiveFile;
    }

    static public ArchiveStoreFile of(ArchiveStore driver, CommonPath path, boolean isFolder) {
        ArchiveStoreFile archiveFile = new ArchiveStoreFile();
        archiveFile.driver = driver;
        archiveFile.localdriver = driver;
        archiveFile.localpath = path;
        archiveFile.with("IsFolder", isFolder);

        archiveFile.refreshProps();

        return archiveFile;
    }

	protected ArchiveStore localdriver = null;
    protected CommonPath localpath = null;
    //protected Path localpath = null;

	//protected FileInputStream input = null; -- don't use FIS
    protected ArchiveEntry entry = null;
	//protected long offset = 0;
		
	public ArchiveStoreFile() {
        this.withType(SchemaHub.getType("dcArchiveStoreFile"));
	}

    public void refreshProps() {
        String fpath = this.localpath.toString();

        if (this.entry == null)
	        this.entry = this.lookupFile();

	    if (this.entry != null) {
            fpath = "/".equals(this.entry.getName()) ? "/" : "/" + this.entry.getName();

            this.with("Size", entry.getSize());
            this.with("Modified", entry.getLastModifiedDate().toInstant());
            this.with("IsFolder", entry.isDirectory());
            this.with("Exists", true);
        }
	    else {
            this.with("Exists", false);
            this.removeField("Size");
            this.removeField("Modified");
            this.removeField("IsFolder");
        }

        String cwd = this.driver.getFieldAsString("RootFolder");

	    // catches root too, check first
        if (fpath.length() == cwd.length())
            this.with("Path", "/");
	    else if (cwd.length() == 1)
            this.with("Path", fpath.replace('\\', '/'));
        else
            this.with("Path", "/" + fpath.substring(cwd.length() + 1).replace('\\', '/'));

        this.withConfirmed(true);
    }

    public ArchiveEntry lookupFile() {
        String path = this.localpath.toString();

        if (path.equals("/")) {
            ZipArchiveEntry entry = new ZipArchiveEntry("/");
            return entry;
        }

        try (ZipArchiveInputStream zin = this.localdriver.getArchiveReadAccess()) {
            ArchiveEntry entry = zin.getNextEntry();

            while (entry != null) {
                String ename = "/" + entry.getName();

                if (ename.equals(path)) {
                    return entry;
                }

                entry = zin.getNextEntry();
            }
        }
        catch (IOException x) {
            Logger.error("Problem listing files: " + x);
        }

        return null;
    }

    // caller must close
    public ZipArchiveInputStream seekReadAccess() {
        ZipArchiveInputStream zin = this.localdriver.getArchiveReadAccess();

        try {
            ArchiveEntry entry = zin.getNextEntry();

            String path = this.getFieldAsString("Path");

            while (entry != null) {
                String ename = "/" + entry.getName();

                if (ename.equals(path)) {
                    return zin;
                }

                entry = zin.getNextEntry();
            }
        }
        catch (IOException x) {
            Logger.error("Problem listing files: " + x);
        }

        return null;
    }

    public Memory readFile() {
        try (ZipArchiveInputStream zin = this.localdriver.getArchiveReadAccess()) {
            ArchiveEntry entry = zin.getNextEntry();

            String path = this.getFieldAsString("Path");

            while (entry != null) {
                String ename = "/" + entry.getName();

                if (ename.equals(path)) {
                    int esize = (int) entry.getSize();

                    if (esize > 0) {
                        int eleft = esize;
                        byte[] buff = new byte[esize];
                        int offset = 0;

                        // TODO sometimes it takes more than one read to get an entry - who knows why
                        // anyway, there is probably an nicer way to do this (see also JarLibLoader)
                        while (offset < esize) {
                            int d = zin.read(buff, offset, eleft);
                            offset += d;
                            eleft -= d;
                        }

                        Memory treem = new Memory(buff);
                        treem.setPosition(0);

                        return treem;
                    }
                }

                entry = zin.getNextEntry();
            }
        }
        catch (IOException x) {
            Logger.error("Problem listing files: " + x);
        }

        return null;
    }

    @Override
    public ArchiveStore getDriver() {
        return this.localdriver;
    }

    @Override
    public void getAttribute(String name, OperationOutcome<Struct> callback) {
        // TODO fix this
        Logger.error("attrs not supported yet");
        callback.returnResult();
    }

    @Override
    public IFileCollection scanner() {
        if (this.isFolder())
            return ArchiveStoreScanner.of(this.getDriver(), this.getPathAsCommon());

        return null;
    }

    @Override
    public void getFolderListing(OperationOutcome<List<FileStoreFile>> callback) {
        List<FileStoreFile> files = new ArrayList<>();

        try (ZipArchiveInputStream zin = this.localdriver.getArchiveReadAccess()) {
            ArchiveEntry entry = zin.getNextEntry();

            String path = this.getFieldAsString("Path");

            if (! "/".equals(path))
                path += "/";

            while (entry != null) {
                String ename = "/" + entry.getName().replace('\\', '/');

                int pos = ename.indexOf(path);
                int pos2 = ename.indexOf('/', path.length());

                // check that it is in the folder and not a sub folder
                // the later half of the check is for the root folder case
                if ((pos == 0) && ((! entry.isDirectory() && (pos2 == -1)) || (entry.isDirectory() && (pos2 == ename.length() - 1)))) {
                    ArchiveStoreFile f = ArchiveStoreFile.of(ArchiveStoreFile.this.getDriver(), entry);
                    files.add(f);
                }

                entry = zin.getNextEntry();
            }

            callback.returnValue(files);
        }
        catch (IOException x) {
            Logger.error("Problem listing files: " + x);

            callback.returnValue(null);
        }
    }

    @Override
    public StreamFragment allocStreamDest() {
        // TODO return StreamFragment.of(LocalDestStream.from(this));
        return null;
    }

    @Override
    public StreamFragment allocStreamSrc() {
        if (this.isFolder())
            return StreamFragment.of(CollectionSourceStream.of(this.scanner()));

        return StreamFragment.of(ArchiveSourceStream.of(this));
    }

    @Override
    protected void doCopy(Struct n) {
        super.doCopy(n);

        ArchiveStoreFile nn = (ArchiveStoreFile)n;
        nn.driver = this.driver;
        nn.entry = this.entry;
    }

    @Override
    public ArchiveStoreFile deepCopy() {
        ArchiveStoreFile cp = new ArchiveStoreFile();
        this.doCopy(cp);
        return cp;
    }

    @Override
    public Struct getOrAllocateField(String name) {
        // TODO consider this
        //if ("TextReader".equals(name))
        //	return new FileSystemTextReader(this);

        return super.getOrAllocateField(name);
    }

    @Override
    public ReturnOption operation(StackWork stack, XElement code) throws OperatingContextException {
        /* TODO
        if ("Hash".equals(code.getName())) {
            String meth = StackUtil.stringFromElement(stack, code, "Method");

            Struct var = StackUtil.refFromElement(stack, code, "Target", true);

            if (var instanceof ScalarStruct) {
                try {
                    this.hash(meth, new OperationOutcome<String>() {
                        @Override
                        public void callback(String result) throws OperatingContextException {
                            ((ScalarStruct)var).adaptValue(result);

                            stack.setState(ExecuteState.DONE);

                            OperationContext.getAsTaskOrThrow().resume();
                        }
                    });

                    return ReturnOption.AWAIT;
                }
                catch (OperatingContextException x) {
                    Logger.error("Context error in Hash: " + x);
                }
            }
            else {
                Logger.error("Invalid hash target!");
            }

            return ReturnOption.CONTINUE;
        }

        if ("Rename".equals(code.getName())) {
            String val = StackUtil.stringFromElement(stack, code, "Value");

            if (StringUtil.isEmpty(val)) {
                Logger.error("Unable to rename, missing new name.");
                return ReturnOption.CONTINUE;
            }

            Path dest = this.localpath.getParent().resolve(val);

            try {
                Files.move(this.localpath, dest, StandardCopyOption.REPLACE_EXISTING);

                // TODO this.driver.fireFolderEvent(this.localpath, StandardWatchEventKinds.ENTRY_MODIFY);

                this.localpath = dest;
                this.refreshProps();
            }
            catch (IOException x) {
                Logger.error("Unable to rename file: + x");
            }

            return ReturnOption.CONTINUE;
        }

        // this is kind of a hack - may want to re-evaluate this later
        // used by NCC provisioning
        if ("WriteText".equals(code.getName())) {
            String text = code.getText();

            Struct content = StringUtil.isNotEmpty(text)
                    ? StackUtil.resolveReference(stack, text, true)
                    : StackUtil.refFromElement(stack, code, "Target", true);

            if (content != null) {
                IOUtil.saveEntireFile(this.localpath, Struct.objectToString(content));
                this.refreshProps();

                this.driver.fireFolderEvent(this.localpath, StandardWatchEventKinds.ENTRY_MODIFY);
            }

            return ReturnOption.CONTINUE;
        }

        // this is kind of a hack - may want to re-evaluate this later
        // used by NCC provisioning
        if ("ReadText".equals(code.getName())) {
            if (this.getFieldAsBooleanOrFalse("Exists")) {
                Struct var = StackUtil.refFromElement(stack, code, "Target", true);

                //System.out.println("e: " + var);

                if ((var == null) || (var instanceof NullStruct)) {
                    String handle = StackUtil.stringFromElement(stack, code, "Result");

                    if (handle != null)
                        StackUtil.addVariable(stack, handle, StringStruct.of(IOUtil.readEntireFile(this.localpath)));
                }
                else if (var instanceof ScalarStruct) {
                    ((ScalarStruct)var).adaptValue(IOUtil.readEntireFile(this.localpath));
                }
                else {
                    Logger.error("Unable to ReadText, bad target.");
                }
            }

            return ReturnOption.CONTINUE;
        }

        if ("Delete".equals(code.getName())) {
            try {
                if (this.isFolder())
                    FileUtil.deleteDirectory(this.localpath);
                else
                    Files.deleteIfExists(this.localpath);

                this.driver.fireFolderEvent(this.localpath, StandardWatchEventKinds.ENTRY_DELETE);
            }
            catch (IOException x) {
                Logger.error("Unable to delete folder: " + x);
            }

            this.refreshProps();

            return ReturnOption.CONTINUE;
        }

         */

        return super.operation(stack, code);
    }

    @Override
    public void readAllText(OperationOutcome<String> callback) {
        Memory bin = this.readFile();

        if (bin == null) {
            Logger.error("Could not find file content");
            callback.returnEmpty();
        }
        else {
            callback.returnValue(bin.toString());
        }
    }

    @Override
    public void writeAllText(String v, OperationOutcomeEmpty callback) {
        /*
        IOUtil.saveEntireFile(this.localpath, v);

        this.driver.fireFolderEvent(this.localpath, StandardWatchEventKinds.ENTRY_MODIFY);

        callback.returnResult();

         */

        Logger.error("write all not supported yet");
        callback.returnEmpty();
    }

    @Override
    public void readAllBinary(OperationOutcome<Memory> callback) {
        Memory bin = this.readFile();

        if (bin == null)
            Logger.error("Could not find file content");

        callback.returnValue(bin);
    }

    @Override
    public void writeAllBinary(Memory v, OperationOutcomeEmpty callback) {
        /*
        IOUtil.saveEntireFile(this.localpath, v);

        this.driver.fireFolderEvent(this.localpath, StandardWatchEventKinds.ENTRY_MODIFY);

        callback.returnResult();
         */
        Logger.error("write all not supported yet");
        callback.returnEmpty();
    }

    @Override
    public void hash(String method, OperationOutcome<String> callback) {
        Memory bin = this.readFile();

        if (bin == null) {
            Logger.error("Could not find file content, unable to hash");
            callback.returnEmpty();
        }
        else {
            String res = HashUtil.hash(method, new InputWrapper(bin));

            callback.returnValue(res);
        }
    }

    @Override
    public void rename(String name, OperationOutcomeEmpty callback) {
        // TODO fix this
        Logger.error("rename not supported yet");
        callback.returnResult();
    }

    @Override
    public void remove(OperationOutcomeEmpty callback) {
    /*
        if (this.exists()) {
            if (this.isFolder()) {
                FileUtil.deleteDirectory(this.localpath);
            }
            else  {
                try {
                    Files.delete(this.localpath);

                    this.driver.fireFolderEvent(this.localpath, StandardWatchEventKinds.ENTRY_DELETE);
                }
                catch (Exception x) {
                    Logger.error("Unable to remove file: " + this.getPath() + " - Error: " + x);
                }
            }
        }

        if (callback != null)
            callback.returnResult();

     */
        Logger.error("remove not supported yet");
        callback.returnEmpty();
    }
}
