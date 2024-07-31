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
import dcraft.log.Logger;
import dcraft.struct.ListStruct;
import dcraft.struct.RecordStruct;
import dcraft.struct.Struct;
import dcraft.task.IWork;
import dcraft.task.TaskContext;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;

public class MainAdapter extends BaseAdapter {
	@Override
	public void init(RecordStruct request) throws OperatingContextException {
		super.init(request);

		this.resolvingResource = ResourceHub.getResources().getDoc();
		this.basePath = CommonPath.from("/");
	}

	@Override
	protected void init(TaskContext taskContext) throws OperatingContextException {
		super.init(taskContext);

		//RecordStruct proc = Struct.objectToRecord(taskContext.queryVariable("_Process"));
		RecordStruct resp = this.request.getFieldAsRecord("Response");
		CommonPath path = CommonPath.from(this.request.getFieldAsString("Path"));
		ListStruct locales = this.request.getFieldAsList("Locales");

		String ftitle = "Main: " + path.toString();

		ResourceFileInfo filematch = this.findMarkdownFile(path.getParent(), path.getFileName(), locales);

		if ((filematch == null) && this.hasFolder(path)) {
			filematch = this.findMarkdownFile(path, "index", locales);
		}

		if (filematch != null) {
			StringBuilder sb = new StringBuilder();

			this.textToScript(this.request, sb, filematch);

			this.then(new IWork() {
				@Override
				public void run(TaskContext taskctx) throws OperatingContextException {
					resp.with("Title", ftitle);
					resp.with("Text", sb.toString());
					taskctx.returnEmpty();
				}
			});
		}
		else {
			Logger.warn("Missing or bad document path: " + path + " in main docs");
			resp.with("Text", "file not found: " + path);
			taskContext.returnEmpty();
			return;
		}
	}
}
