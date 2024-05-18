package dcraft.mail;

import dcraft.filestore.CommonPath;
import dcraft.hub.ResourceHub;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
import dcraft.hub.op.OperationMarker;
import dcraft.log.Logger;
import dcraft.log.count.CountHub;
import dcraft.schema.DataType;
import dcraft.script.StackUtil;
import dcraft.struct.BaseStruct;
import dcraft.struct.RecordStruct;
import dcraft.task.ChainWork;
import dcraft.task.IWork;
import dcraft.task.TaskContext;
import dcraft.tenant.Site;
import dcraft.util.StringUtil;
import dcraft.xml.XElement;

public class EmailRequestWork extends ChainWork {
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
			CommInfo commInfo = CommInfo.of(path);

			if (om.hasErrors() || (commInfo == null)) {
				Logger.error("Comm file was not found or not configured.");
				taskctx.returnEmpty();
				return;
			}

			// validate the argument data

			BaseStruct arg = req.getField("Args");

			DataType rdt = commInfo.opInfo.getOp().getRequest();

			// if there is data there better be a type to validate against
			if ((rdt == null) && (arg != null)) {
				Logger.error("Missing schema for comm request");
				taskctx.returnEmpty();
				return;
			}

			if (rdt != null) {
				try (OperationMarker omi = OperationMarker.create()) {
					arg = rdt.normalizeValidate(true, false, arg);

					// TODO this check should probably be in normalizeValidate, but that requires other checking
					if ((arg == null) && (rdt.isRequired()))
						Logger.error("Data body is missing.");

					// TODO find other calls to normalizeValidate and change to use OM
					if (omi.hasErrors()) {
						Logger.error("Unable to validate and normalize request comm.");
						taskctx.returnEmpty();
						return;
					}
				}
				catch (Exception x) {
					Logger.error("Unable to validate and normalize request comm: " + x);
					taskctx.returnEmpty();
					return;
				}
			}

			BaseStruct finalarg = arg;

			req.with("Args", finalarg);

			// make $_Param available to all scripts and templates

			taskctx.addVariable("_Param", finalarg);

			RecordStruct resp = RecordStruct.record();

			// make $_Process available to all scripts and templates

			RecordStruct comm = RecordStruct.record()
					.with("Target", "Email")
					.with("Request", req)
					.with("Response", resp)
					.with("Data", RecordStruct.record())
					.with("Config", commInfo.config)
					.with("Folder", commInfo.folder);

			taskctx.addVariable("_Process", comm);

			// run initialize script if present

			if (commInfo.initalize != null) {
				System.out.println("add init");

				this.then(commInfo.initalize.toWork());

				// make sure our chain has the correct arguments for the next step

				this.then(new IWork() {
					@Override
					public void run(TaskContext taskctx) throws OperatingContextException {
						taskctx.returnValue(finalarg);
					}
				});
			}

			// after data is loaded by init, expand the Subject  (most useful for text / basic emails only, dcc and dcs can set the Subject directly)

			String startSubject = commInfo.config.getFieldAsString("Subject");

			if (StringUtil.isNotEmpty(startSubject)) {
				startSubject = StackUtil.resolveValueToString(this, startSubject);
				commInfo.config.with("Subject", startSubject);
			}

			// now figure out which adapters need to run

			System.out.println("add html adapter if present");

			String htmlHandler = commInfo.config.selectAsString("Handler/HtmlEmail");

			if (StringUtil.isNotEmpty(htmlHandler)) {
				XElement handlerSettings = ResourceHub.getResources().getComm().getEmailHandler(htmlHandler);

				if (handlerSettings != null) {
					String adapterClass = handlerSettings.attr("Adapter");

					if (StringUtil.isNotEmpty(adapterClass)) {
						try {
							IEmailOutputWork outputWork = (IEmailOutputWork) taskctx.getSite().getResources().getClassLoader().getInstance(adapterClass);

							outputWork.init(commInfo);

							System.out.println("add output");

							this.then(outputWork);

							// make sure our chain has the correct arguments for the next step

							this.then(new IWork() {
								@Override
								public void run(TaskContext taskctx) throws OperatingContextException {
									taskctx.returnValue(finalarg);
								}
							});
						}
						catch (Exception x) {
							Logger.error("Bad Comm Adapter: " + x);
							taskctx.returnEmpty();
							return;
						}
					}
				}
			}

			System.out.println("add text adapter if present");

			String textHandler = commInfo.config.selectAsString("Handler/TextEmail");

			if (StringUtil.isNotEmpty(textHandler)) {
				XElement handlerSettings = ResourceHub.getResources().getComm().getEmailHandler(textHandler);

				if (handlerSettings != null) {
					String adapterClass = handlerSettings.attr("Adapter");

					if (StringUtil.isNotEmpty(adapterClass)) {
						try {
							IEmailOutputWork outputWork = (IEmailOutputWork) taskctx.getSite().getResources().getClassLoader().getInstance(adapterClass);

							outputWork.init(commInfo);

							System.out.println("add output");

							this.then(outputWork);

							// make sure our chain has the correct arguments for the next step

							this.then(new IWork() {
								@Override
								public void run(TaskContext taskctx) throws OperatingContextException {
									taskctx.returnValue(finalarg);
								}
							});
						}
						catch (Exception x) {
							Logger.error("Bad Comm Adapter: " + x);
							taskctx.returnEmpty();
							return;
						}
					}
				}
			}

			System.out.println("add reply");

			this.then(new IWork() {
				@Override
				public void run(TaskContext taskctx) throws OperatingContextException {
					System.out.println("reply with request plus additions: " + resp);

					String finalSubject = commInfo.config.getFieldAsString("Subject");

					if (StringUtil.isNotEmpty(finalSubject))
						finalSubject = finalSubject.trim();

					resp.with("Subject", finalSubject);

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
