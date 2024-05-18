package dcraft.mail.adapter;

import dcraft.hub.ResourceHub;
import dcraft.hub.op.OperatingContextException;
import dcraft.mail.CommInfo;
import dcraft.mail.MailUtil;
import dcraft.script.StackUtil;
import dcraft.struct.RecordStruct;
import dcraft.task.IWork;
import dcraft.task.TaskContext;
import dcraft.util.IOUtil;

import java.nio.file.Path;

public class HtmlBasicAdapter extends BaseAdapter {
	protected Path file = null;

	@Override
	public void init(CommInfo info) throws OperatingContextException {
		super.init(info);

		// TODO search locales

		this.file = ResourceHub.getResources().getComm().findCommFile(info.folder.resolve("code-email.html"));
	}

	@Override
	protected void init(TaskContext taskctx) throws OperatingContextException {
		super.init(taskctx);

		StringBuilder htmlBuffer = new StringBuilder();
		StringBuilder textBuffer = new StringBuilder();

		this.then(new IWork() {
			@Override
			public void run(TaskContext taskctx) throws OperatingContextException {
				CharSequence html = MailUtil.processSSIIncludes(IOUtil.readEntireFile(HtmlBasicAdapter.this.file));

				html = StackUtil.resolveValueToString(HtmlBasicAdapter.this, html.toString());

				// TODO support macros?

				htmlBuffer.append(html);

				taskctx.returnEmpty();
			}
		});

		// TODO look for a txt file as well, use that for text

		this.then(new IWork() {
			@Override
			public void run(TaskContext taskctx) throws OperatingContextException {
				taskctx.returnValue(RecordStruct.record()
						.with("html", htmlBuffer)
						.with("text", textBuffer)
				);
			}
		});
	}
}
