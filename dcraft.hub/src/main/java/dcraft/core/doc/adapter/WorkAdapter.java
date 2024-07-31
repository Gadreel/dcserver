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

import dcraft.filestore.CommonPath;
import dcraft.hub.ResourceHub;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.resource.ResourceFileInfo;
import dcraft.struct.ListStruct;
import dcraft.struct.RecordStruct;
import dcraft.struct.Struct;
import dcraft.task.IWork;
import dcraft.task.TaskContext;

import java.nio.file.Path;
import java.nio.file.Paths;

public class WorkAdapter extends LWAdapter {
	@Override
	public void init(RecordStruct request) throws OperatingContextException {
		super.init(request);

		this.resolvingResource = ResourceHub.getResources().getScripts();
		this.basePath = CommonPath.from("/work");
	}

	@Override
	protected void init(TaskContext taskContext) throws OperatingContextException {
		super.init(taskContext);

		RecordStruct proc = Struct.objectToRecord(taskContext.queryVariable("_Process"));
		CommonPath path = CommonPath.from(this.request.getFieldAsString("Path"));

		String title = "Work Folder: " + path;

		StringBuilder sb = new StringBuilder();

		if (this.iamascript) {
			title = "Work Name: " + path;

			sb.append("Work Name: " + path + "\n\n");

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
		}
		else {
			sb.append("Work Folder: " + path + "\n\n");

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

			ListStruct locales = this.request.getFieldAsList("Locales");

			if (! this.addendums.isEmpty()) {
				this.then(new IWork() {
					@Override
					public void run(TaskContext taskctx) throws OperatingContextException {
						sb.append("## Addendums\n\n");

						taskctx.returnEmpty();
					}
				});

				for (ResourceFileInfo addendum : this.addendums) {
					this.then(new IWork() {
						@Override
						public void run(TaskContext taskctx) throws OperatingContextException {
							Path opath = Paths.get("./").toAbsolutePath();

							sb.append("### Addendum Path " + opath.relativize(addendum.getActualPath()) + "\n\n");

							taskctx.returnEmpty();
						}
					});

					this.textToScript(proc, sb, addendum);

					this.then(new IWork() {
						@Override
						public void run(TaskContext taskctx) throws OperatingContextException {
							sb.append("\n\n");

							taskctx.returnEmpty();
						}
					});
				}
			}

			if (! this.subscripts.isEmpty()) {
				this.then(new IWork() {
					@Override
					public void run(TaskContext taskctx) throws OperatingContextException {
						sb.append("## Work Scripts\n\n");

						taskctx.returnEmpty();
					}
				});

				for (CommonPath script : this.subscripts) {
					if (! script.getFileName().endsWith(".dcs.xml"))
						continue;

					String sname = script.toString();

					String fsname = sname.substring(0, sname.length() - 8);

					this.then(new IWork() {
						@Override
						public void run(TaskContext taskctx) throws OperatingContextException {
							sb.append("- [work" + fsname + "](dc-docs://work" + fsname + ")\n");

							taskctx.returnEmpty();
						}
					});

					ResourceFileInfo serviceSummary = this.findMarkdownFile(script, "summary", locales);

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

				this.then(new IWork() {
					@Override
					public void run(TaskContext taskctx) throws OperatingContextException {
						sb.append("\n");

						taskctx.returnEmpty();
					}
				});
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
							sb.append("### [work" + folder + "](dc-docs://work" + folder + ")\n\n");

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
