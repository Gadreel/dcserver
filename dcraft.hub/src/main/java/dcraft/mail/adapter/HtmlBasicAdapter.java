package dcraft.mail.adapter;

import dcraft.hub.ResourceHub;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
import dcraft.mail.CommInfo;
import dcraft.mail.MailUtil;
import dcraft.script.Script;
import dcraft.script.StackUtil;
import dcraft.struct.RecordStruct;
import dcraft.struct.Struct;
import dcraft.task.IWork;
import dcraft.task.TaskContext;
import dcraft.util.IOUtil;
import dcraft.util.StringUtil;
import dcraft.web.md.MarkdownUtil;
import dcraft.xml.XElement;

import java.nio.file.Files;
import java.nio.file.Path;

public class HtmlBasicAdapter extends SimpleAdapter {
	@Override
	public void init(CommInfo info) throws OperatingContextException {
		super.init(info, ".html");
	}

	@Override
	protected void textToScript(RecordStruct proc, RecordStruct resp, CharSequence text) throws OperatingContextException {
		String code = "<dcs.Script><body Name=\"body\">\n" + text + "\n</text><dcs.Exit Result=\"$body\" /></dcs.Script>";

		Script script = Script.of(code);

		this.then(script);

		this.then(new IWork() {
			@Override
			public void run(TaskContext taskctx) throws OperatingContextException {
				resp.with("Html", taskctx.getResult());

				taskctx.returnValue(resp);
			}
		});
	}
}
