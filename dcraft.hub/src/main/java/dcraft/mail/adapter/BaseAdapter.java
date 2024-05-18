package dcraft.mail.adapter;

import dcraft.filestore.CommonPath;
import dcraft.hub.op.OperatingContextException;
import dcraft.log.count.CountHub;
import dcraft.mail.CommInfo;
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
    protected CommInfo commInfo = null;

    //protected RecordStruct result = RecordStruct.record();

    @Override
    public CommInfo getInfo() {
        return this.commInfo;
    }

    /*
    public CommonPath getPath() {
        return this.commInfo.folder;
    }

     */

    @Override
    public void init(CommInfo info) throws OperatingContextException {
        this.commInfo = info;
    }

    @Override
    protected void init(TaskContext taskctx) throws OperatingContextException {
        super.init(taskctx);

        CountHub.countObjects("dcCommRunCount-" + taskctx.getTenant().getAlias(), this);
    }
}
