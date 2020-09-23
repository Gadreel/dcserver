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
package dcraft.web.adapter;

import dcraft.filestore.CommonPath;
import dcraft.hub.ResourceHub;
import dcraft.hub.op.OperatingContextException;
import dcraft.script.Script;
import dcraft.task.ChainWork;
import dcraft.task.TaskContext;
import dcraft.tenant.Site;
import dcraft.util.MimeInfo;
import dcraft.web.IReviewWork;
import dcraft.web.ui.UIUtil;

import java.nio.file.Path;

public class DynamicReviewAdapter extends ChainWork implements IReviewWork {
	public CommonPath webpath = null;
	public Path file = null;
	protected MimeInfo mime = null;
	protected Script script = null;

	public Path getFile() {
		return this.file;
	}
	
	@Override
	public CommonPath getPath() {
		return this.webpath;
	}

	@Override
	public MimeInfo getMime() {
		return this.mime;
	}

	@Override
	public Script getScript() {
		return this.script;
	}

	@Override
	public void init(Site site, Path file, CommonPath web, String view) {
		this.webpath = web;
		this.file = file;
		this.mime = ResourceHub.getResources().getMime().getMimeTypeForPath(this.file);
	}

	public Script getSource() throws OperatingContextException {
		if (this.script != null)
			return this.script;
		
		this.script = Script.of(this.file);

		return this.script;
	}
	
	@Override
	protected void init(TaskContext ctx) throws OperatingContextException {
		Script script = this.getSource();

		if (script == null) {
			ctx.clearExitCode();
			ctx.complete();
			return;
		}

		this
				.then(UIUtil.dynamicToWork(ctx, script));
	}
}
