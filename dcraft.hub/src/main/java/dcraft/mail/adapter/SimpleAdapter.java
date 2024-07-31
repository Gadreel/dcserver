package dcraft.mail.adapter;

import dcraft.hub.ResourceHub;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
import dcraft.log.Logger;
import dcraft.mail.CommInfo;
import dcraft.mail.MailUtil;
import dcraft.script.Script;
import dcraft.script.StackUtil;
import dcraft.struct.RecordStruct;
import dcraft.struct.Struct;
import dcraft.task.IWork;
import dcraft.task.TaskContext;
import dcraft.util.IOUtil;

import java.nio.file.Files;
import java.nio.file.Path;

abstract public class SimpleAdapter extends BaseAdapter {
	protected Path file = null;

	public void init(CommInfo info, String ext) throws OperatingContextException {
		super.init(info);

		String locale = info.locale;

		this.file = ResourceHub.getResources().getComm().findCommFile(info.folder.resolve("code-email." + locale + ext));

		if ((this.file == null) || ! Files.exists(this.file)) {
			locale = OperationContext.getOrThrow().getSite().getResources().getLocale().getDefaultLocale();

			this.file = ResourceHub.getResources().getComm().findCommFile(info.folder.resolve("code-email." + locale + ext));
		}
	}

	@Override
	protected void init(TaskContext taskctx) throws OperatingContextException {
		super.init(taskctx);

		if (this.file == null) {
			Logger.error("Missing or bad email text template: " + this.commInfo.folder);
			taskctx.returnEmpty();
			return;
		}

		RecordStruct proc = Struct.objectToRecord(taskctx.queryVariable("_Process"));

		if (proc == null) {
			Logger.error("Missing comm process for email text template: " + this.commInfo.folder);
			taskctx.returnEmpty();
			return;
		}

		RecordStruct resp = proc.getFieldAsRecord("Response");

		if (resp == null) {
			Logger.error("Missing comm response for email text template: " + this.commInfo.folder);
			taskctx.returnEmpty();
			return;
		}

		CharSequence text = MailUtil.processSSIIncludes(IOUtil.readEntireFile(SimpleAdapter.this.file));

		// TODO support macros?

		// later? - otherwise resolved data can trigger issues with < and > in the xml
		//text = StackUtil.resolveValueToString(SimpleAdapter.this, text.toString());

		this.textToScript(proc, resp, text);
	}

	abstract protected void textToScript(RecordStruct proc, RecordStruct resp, CharSequence text) throws OperatingContextException;
}
