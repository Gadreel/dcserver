package dcraft.mail.adapter;

import dcraft.filestore.CommonPath;
import dcraft.hub.op.OperatingContextException;
import dcraft.mail.IEmailOutputWork;
import dcraft.struct.ListStruct;
import dcraft.struct.RecordStruct;
import dcraft.task.ChainWork;
import dcraft.task.TaskContext;

import java.nio.file.Path;

// create a Result record with "html" (String), "text" (String) and possibly "attachments" (List)
// TODO add some documentation (Type: Local, Path: localpath, Mime: nnn, Name, mmm)
// TODO support stream attachments someday

abstract public class BaseAdapter extends ChainWork implements IEmailOutputWork {
    protected CommonPath emailpath = null;
    protected Path file = null;
    protected String view = null;

    protected boolean runonce = false;

    protected RecordStruct result = RecordStruct.record();

    public Path getFile() {
        return this.file;
    }

    @Override
    public CommonPath getPath() {
        return this.emailpath;
    }

    @Override
    public void init(Path file, CommonPath email, String view) throws OperatingContextException {
        this.emailpath = email;
        this.file = file;
        this.view = view;
    }
}
