package dcraft.mail.adapter;

import dcraft.hub.op.OperatingContextException;
import dcraft.mail.CommInfo;
import dcraft.script.Script;
import dcraft.struct.RecordStruct;
import dcraft.task.IWork;
import dcraft.task.TaskContext;

public class EmailTextAdapter extends SimpleAdapter {
	@Override
	public void init(CommInfo info) throws OperatingContextException {
		super.init(info, ".txt");
	}

	protected void textToScript(RecordStruct proc, RecordStruct resp, CharSequence text) throws OperatingContextException {
		String code = "<dcs.Script><text Name=\"text\">\n" + text + "\n</text><dcs.Exit Result=\"$text\" /></dcs.Script>";

		Script script = Script.of(code);

		this.then(script);

		this.then(new IWork() {
			@Override
			public void run(TaskContext taskctx) throws OperatingContextException {
				resp.with("Text", taskctx.getResult());

				taskctx.returnValue(resp);
			}
		});
	}
}
