package dcraft.filevault;

import dcraft.filestore.CommonPath;
import dcraft.filestore.FileDescriptor;

import java.time.ZonedDateTime;

public class TransactionFile {
    static public TransactionFile of(CommonPath path, ZonedDateTime modified) {
        TransactionFile f = new TransactionFile();
        f.path = path;
        f.modified = modified;
        return f;
    }

    static public TransactionFile of(FileDescriptor descriptor) {
        TransactionFile f = new TransactionFile();
        f.path = descriptor.getPathAsCommon();
        f.modified = descriptor.getModificationAsTime();
        return f;
    }

    protected CommonPath path = null;
    protected ZonedDateTime modified = null;

    public CommonPath getPath() {
        return this.path;
    }

    public ZonedDateTime getTimestamp() {
        return this.modified;
    }
}
