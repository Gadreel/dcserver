package dcraft.core.doc;

import dcraft.filestore.CommonPath;
import dcraft.hub.ResourceHub;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
import dcraft.hub.op.OperationMarker;
import dcraft.log.Logger;
import dcraft.schema.DataType;
import dcraft.script.StackUtil;
import dcraft.struct.BaseStruct;
import dcraft.struct.ListStruct;
import dcraft.struct.RecordStruct;
import dcraft.task.ChainWork;
import dcraft.task.IWork;
import dcraft.task.TaskContext;
import dcraft.tenant.Site;
import dcraft.util.StringUtil;
import dcraft.web.md.MarkdownUtil;
import dcraft.xml.XElement;

public class DocRequestWork extends ChainWork {
	@Override
	protected void init(TaskContext taskctx) throws OperatingContextException {
		super.init(taskctx);

		RecordStruct req = taskctx.getFieldAsRecord("Params");

		String domain = req.getFieldAsString("Domain");
		CommonPath path = CommonPath.from(req.getFieldAsString("Path"));

		Site webSite = taskctx.getSite();

		if (Logger.isDebug())
			Logger.debug("Document Site: " + webSite.getAlias() + " - Document Translating path: " + domain + " : " + path);

		ListStruct locales = ListStruct.list();

		String currlocale = OperationContext.getOrThrow().getLocale();
		String deflocale = OperationContext.getOrThrow().getSite().getResources().getLocale().getDefaultLocale();

		locales.with(currlocale);

		if (! deflocale.equals(currlocale))
			locales.with(deflocale);

		req.with("Locales", locales);

		// Response should have:
		//		Title				for doc viewer
		//		DisplayPath			as reference for viewer
		//		Markdown			fully assembled markdown
		RecordStruct resp = RecordStruct.record();

		// make $_Process available to all scripts and templates

		req.with("Response", resp);

		taskctx.addVariable("_Process", req);

		try (OperationMarker om = OperationMarker.create()) {

			// now figure out which adapters need to run

			System.out.println("add adapter if present");

			XElement handlerSettings = ResourceHub.getResources().getDoc().getDocHandler(domain);

			if (handlerSettings != null) {
				String adapterClass = handlerSettings.attr("Adapter");

				if (StringUtil.isNotEmpty(adapterClass)) {
					try {
						IDocOutputWork outputWork = (IDocOutputWork) taskctx.getSite().getResources().getClassLoader().getInstance(adapterClass);

						outputWork.init(req);

						System.out.println("add output");

						this.then(outputWork);
					}
					catch (Exception x) {
						Logger.error("Bad Doc Adapter: " + x);
						taskctx.returnEmpty();
						return;
					}
				}
			}

			System.out.println("add email builder reply");

			this.then(new IWork() {
				@Override
				public void run(TaskContext taskctx) throws OperatingContextException {
					// not null only if it is a document
					BaseStruct output = resp.getField("Text");

					if (output != null) {
						XElement body = MarkdownUtil.process(output.toString(), true);

						resp.with("Html", body);
					}

					taskctx.returnValue(resp);
				}
			});
		}
		catch (Exception x) {
			Logger.error("Unable to process web file: " + x);
			taskctx.returnEmpty();
		}
	}
}
