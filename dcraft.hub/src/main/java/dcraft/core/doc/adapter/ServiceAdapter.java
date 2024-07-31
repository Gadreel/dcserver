/* ************************************************************************
#
#  designCraft.io
#
#  http://designcraft.io/
#
#  Copyright:
#    Copyright 2014 eTimeline, LLC. All rights reserved.
#
#  License:
#    See the license.txt file in the project's top-level directory for details.
#
#  Authors:
#    * Andy White
#
************************************************************************ */
package dcraft.core.doc.adapter;

import dcraft.core.doc.DocUtil;
import dcraft.filestore.CommonPath;
import dcraft.hub.ResourceHub;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.resource.ResourceFileInfo;
import dcraft.struct.CompositeParser;
import dcraft.struct.ListStruct;
import dcraft.struct.RecordStruct;
import dcraft.struct.Struct;
import dcraft.task.IWork;
import dcraft.task.TaskContext;
import dcraft.util.StringUtil;
import dcraft.xml.XElement;
import dcraft.xml.XmlReader;

import java.nio.file.Path;
import java.util.List;

public class ServiceAdapter extends VAdapter {
	@Override
	public void init(RecordStruct request) throws OperatingContextException {
		super.init(request);

		this.resolvingResource = ResourceHub.getResources().getScripts();
		this.basePath = CommonPath.from("/services");
	}

	@Override
	protected void init(TaskContext taskContext) throws OperatingContextException {
		super.init(taskContext);

		RecordStruct proc = Struct.objectToRecord(taskContext.queryVariable("_Process"));
		CommonPath path = CommonPath.from(this.request.getFieldAsString("Path"));
		RecordStruct access = (this.access != null) ? Struct.objectToRecord(CompositeParser.parseJson(this.access.getActualPath())) : null;

		String title = "Service Folder: " + path;

		StringBuilder sb = new StringBuilder();

		if (this.iamavariant) {
			title = "Service Name: " + path;

			sb.append("Service Name: " + path + "\n\n");

			if ((access != null) && access.isNotFieldEmpty("Badges"))
				sb.append("Access: " + access.getFieldAsList("Badges").join(", "));
			else
				sb.append("Access: SysAdmin");

			sb.append("\n\n");

			if (this.summary != null) {
				this.textToScript(proc, sb, this.summary);

				this.then(new IWork() {
					@Override
					public void run(TaskContext taskctx) throws OperatingContextException {
						sb.append("\n\n");

						taskctx.returnEmpty();
					}
				});
			}

			// TODO list addendums ?

			if (this.schema != null) {
				XElement opel = XmlReader.loadFile(this.schema.getActualPath(), false, true);

				if (opel != null) {
					this.then(new IWork() {
						@Override
						public void run(TaskContext taskctx) throws OperatingContextException {
							sb.append("## Schema\n\n``` xml\n");

							sb.append(opel.toPrettyString());

							sb.append("\n```\n\n");

							taskctx.returnEmpty();
						}
					});
				}
			}

			if (this.detail != null) {
				this.then(new IWork() {
					@Override
					public void run(TaskContext taskctx) throws OperatingContextException {
						sb.append("## Detail\n\n");

						taskctx.returnEmpty();
					}
				});

				this.textToScript(proc, sb, this.detail);
			}
		}
		else {
			sb.append("Service Folder: " + path + "\n\n");

			if ((access != null) && access.isNotFieldEmpty("Badges"))
				sb.append("Access: " + access.getFieldAsList("Badges").join(", "));
			else
				sb.append("Access: SysAdmin");

			sb.append("\n\n");

			if (this.summary != null) {
				this.textToScript(proc, sb, this.summary);

				this.then(new IWork() {
					@Override
					public void run(TaskContext taskctx) throws OperatingContextException {
						sb.append("\n\n");

						taskctx.returnEmpty();
					}
				});
			}

			// TODO list addendums ?

			if (this.detail != null) {
				this.then(new IWork() {
					@Override
					public void run(TaskContext taskctx) throws OperatingContextException {
						sb.append("## Detail\n\n");

						taskctx.returnEmpty();
					}
				});

				this.textToScript(proc, sb, this.detail);

				this.then(new IWork() {
					@Override
					public void run(TaskContext taskctx) throws OperatingContextException {
						sb.append("\n\n");

						taskctx.returnEmpty();
					}
				});
			}

			RecordStruct req = taskContext.getFieldAsRecord("Params");

			ListStruct locales = req.getFieldAsList("Locales");

			if (! this.subvariants.isEmpty()) {
				this.then(new IWork() {
					@Override
					public void run(TaskContext taskctx) throws OperatingContextException {
						sb.append("## Services\n\n");

						taskctx.returnEmpty();
					}
				});
				for (CommonPath service : this.subvariants) {
					this.then(new IWork() {
						@Override
						public void run(TaskContext taskctx) throws OperatingContextException {
							sb.append("### [service" + DocUtil.vFolderToFolder(service) + "](dc-docs://service" + DocUtil.vFolderToFolder(service) + ")\n\n");

							taskctx.returnEmpty();
						}
					});

					ResourceFileInfo serviceSummary = this.findMarkdownFile(service, "summary", locales);

					if (serviceSummary != null) {
						this.textToScript(proc, sb, serviceSummary);

						this.then(new IWork() {
							@Override
							public void run(TaskContext taskctx) throws OperatingContextException {
								sb.append("\n\n");

								taskctx.returnEmpty();
							}
						});
					}
				}
			}

			if (! this.subfolders.isEmpty()) {
				this.then(new IWork() {
					@Override
					public void run(TaskContext taskctx) throws OperatingContextException {
						sb.append("## Folders\n\n");

						taskctx.returnEmpty();
					}
				});

				for (CommonPath folder : this.subfolders) {
					this.then(new IWork() {
						@Override
						public void run(TaskContext taskctx) throws OperatingContextException {
							sb.append("### [service" + folder + "](dc-docs://service" + folder + ")\n\n");

							taskctx.returnEmpty();
						}
					});

					ResourceFileInfo serviceSummary = this.findMarkdownFile(folder, "summary", locales);

					if (serviceSummary != null) {
						this.textToScript(proc, sb, serviceSummary);

						this.then(new IWork() {
							@Override
							public void run(TaskContext taskctx) throws OperatingContextException {
								sb.append("\n\n");

								taskctx.returnEmpty();
							}
						});
					}
				}
			}
		}

		String ftitle = title;

		this.then(new IWork() {
			@Override
			public void run(TaskContext taskctx) throws OperatingContextException {
				RecordStruct resp = proc.getFieldAsRecord("Response");

				resp.with("Title", ftitle);
				resp.with("Text", sb.toString());

				taskctx.returnEmpty();
			}
		});
	}
}
