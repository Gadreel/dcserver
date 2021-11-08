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
import dcraft.filestore.FileCollection;
import dcraft.filestore.FileStoreFile;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationOutcome;
import dcraft.log.Logger;
import dcraft.schema.SchemaHub;
import dcraft.struct.BaseStruct;
import dcraft.struct.Struct;
import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream;

import java.io.IOException;
import java.util.ArrayList;

public class ArchiveStoreScanner extends FileCollection {
    static public ArchiveStoreScanner of(ArchiveStore driver) {
        ArchiveStoreScanner scanner = new ArchiveStoreScanner();
        scanner.driver = driver;

        return scanner;
    }

    static public ArchiveStoreScanner of(ArchiveStore driver, CommonPath path) {
        ArchiveStoreScanner scanner = new ArchiveStoreScanner();
        scanner.driver = driver;
        scanner.path = path;

        return scanner;
    }

	protected ArchiveStore driver = null;
    protected CommonPath path = null;

    public ArchiveStore getDriver() {
        return this.driver;
    }

	public ArchiveStoreScanner() {
        this.withType(SchemaHub.getType("dcArchiveStoreScanner"));
	}

    public void collectAll() {
        // don't collect more than once
        if (this.collection != null)
            return;

        this.collection = new ArrayList<>();

        // TODO support this.filter - more effecient
        // TODO support filters/sorting/etc

        try (ZipArchiveInputStream zin = this.getDriver().getArchiveReadAccess()) {
            ArchiveEntry entry = zin.getNextEntry();

            String path = this.getFieldAsString("Path");

            if (! "/".equals(path))
                path += "/";

            while (entry != null) {
                String ename = "/" + entry.getName().replace('\\', '/');

                // don't match the subfolder itself
                if (! ename.equals(path)) {
                    int pos = ename.indexOf(path);

                    // check that it is in the folder or a sub folder
                    if (pos == 0) {
                        ArchiveStoreFile f = ArchiveStoreFile.of(ArchiveStoreScanner.this.getDriver(), entry);
                        this.collection.add(f);
                    }
                }

                entry = zin.getNextEntry();
            }
        }
        catch (IOException x) {
            Logger.error("Problem listing files: " + x);
        }
    }

    @Override
    public void next(OperationOutcome<FileStoreFile> callback) throws OperatingContextException {
        if (this.collection == null)
            this.collectAll();

        super.next(callback);
    }

    @Override
    public void forEach(OperationOutcome<FileStoreFile> callback) throws OperatingContextException {
        if (this.collection == null)
            this.collectAll();

        super.forEach(callback);
    }

    @Override
    protected void doCopy(BaseStruct n) {
        super.doCopy(n);

        ArchiveStoreScanner nn = (ArchiveStoreScanner)n;
        nn.driver = this.driver;
        nn.path = this.path;
    }

    @Override
    public ArchiveStoreScanner deepCopy() {
        ArchiveStoreScanner cp = new ArchiveStoreScanner();
        this.doCopy(cp);
        return cp;
    }
}
