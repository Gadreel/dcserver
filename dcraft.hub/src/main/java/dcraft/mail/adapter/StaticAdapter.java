package dcraft.mail.adapter;

import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
import dcraft.log.count.CountHub;
import dcraft.mail.MailUtil;
import dcraft.script.StackUtil;
import dcraft.struct.RecordStruct;
import dcraft.struct.scalar.StringStruct;
import dcraft.task.IWork;
import dcraft.task.TaskContext;
import dcraft.util.IOUtil;

public class StaticAdapter extends BaseAdapter {
	@Override
	protected void init(TaskContext taskctx) throws OperatingContextException {
		super.init(taskctx);

		CountHub.countObjects("dcEmailStaticCount-" + OperationContext.getOrThrow().getTenant().getAlias(), this);

		StringBuilder htmlBuffer = new StringBuilder();
		StringBuilder textBuffer = new StringBuilder();

		this.then(new IWork() {
			@Override
			public void run(TaskContext taskctx) throws OperatingContextException {
				CharSequence html = MailUtil.processSSIIncludes(IOUtil.readEntireFile(StaticAdapter.this.file), StaticAdapter.this.view);

				html = StackUtil.resolveValueToString(StaticAdapter.this, html.toString());

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
