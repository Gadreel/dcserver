package dcraft.mail.adapter;

import dcraft.hub.ResourceHub;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
import dcraft.log.Logger;
import dcraft.mail.CommInfo;
import dcraft.mail.MailUtil;
import dcraft.script.StackUtil;
import dcraft.struct.RecordStruct;
import dcraft.struct.Struct;
import dcraft.task.IWork;
import dcraft.task.TaskContext;
import dcraft.util.IOUtil;

import java.nio.file.Path;

public class EmailTextAdapter extends BaseAdapter {
	protected Path file = null;

	@Override
	public void init(CommInfo info) throws OperatingContextException {
		super.init(info);

		String locale = OperationContext.getOrThrow().getLocale();

		this.file = ResourceHub.getResources().getComm().findCommFile(info.folder.resolve("code-email." + locale + ".txt"));

		if (this.file == null) {
			locale = OperationContext.getOrThrow().getSite().getResources().getLocale().getDefaultLocale();

			this.file = ResourceHub.getResources().getComm().findCommFile(info.folder.resolve("code-email." + locale + ".txt"));
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

		this.then(new IWork() {
			@Override
			public void run(TaskContext taskctx) throws OperatingContextException {
				CharSequence text = MailUtil.processSSIIncludes(IOUtil.readEntireFile(EmailTextAdapter.this.file));

				// TODO support macros?

				text = StackUtil.resolveValueToString(EmailTextAdapter.this, text.toString());

				resp.with("Text", text);

				taskctx.returnValue(resp);
			}
		});
	}
}
