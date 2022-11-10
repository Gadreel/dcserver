package dcraft.mail;

import dcraft.filestore.CommonPath;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationMarker;
import dcraft.log.Logger;
import dcraft.struct.RecordStruct;
import dcraft.task.ChainWork;
import dcraft.task.TaskContext;
import dcraft.tenant.Site;

public class RequestWork extends ChainWork {
	@Override
	protected void init(TaskContext taskctx) throws OperatingContextException {
		super.init(taskctx);

		RecordStruct req = taskctx.getFieldAsRecord("Params");
		CommonPath path = CommonPath.from(req.getFieldAsString("Path"));
		Site webSite = taskctx.getSite();

		if (path.getNameCount() < 1) {
			Logger.error("Missing path to email");
			taskctx.returnEmpty();
			return;
		}

		if (Logger.isDebug())
			Logger.debug("Email Site: " + webSite.getAlias() + " - Email Translating path: " + path);

		try (OperationMarker om = OperationMarker.create()) {
			// try with path case as is (should be lowercase anyway)
			IEmailOutputWork output = MailUtil.emailFindFile(webSite, path, req.getFieldAsString("View"));

			if (om.hasErrors() || (output == null)) {
				Logger.error("Not found page was not found.");
				taskctx.returnEmpty();
				return;
			}

			req.with("OriginalPath", path.toString());
			req.with("Path", output.getPath().toString());

			if (Logger.isDebug())
				Logger.debug("Executing adapter: " + output.getClass().getName());

			this.then(output);
		}
		catch (Exception x) {
			Logger.error("Unable to process web file: " + x);
			taskctx.returnEmpty();
		}
	}
}
