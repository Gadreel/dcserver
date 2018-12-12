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
import dcraft.log.count.CountHub;
import dcraft.script.Script;
import dcraft.script.StackUtil;
import dcraft.stream.StreamFragment;
import dcraft.struct.scalar.BooleanStruct;
import dcraft.task.ChainWork;
import dcraft.task.TaskContext;
import dcraft.tenant.Site;
import dcraft.util.MimeInfo;
import dcraft.util.web.DateParser;
import dcraft.web.HttpDestStream;
import dcraft.web.IOutputWork;
import dcraft.web.WebController;
import dcraft.web.ui.UIUtil;
import io.netty.handler.codec.http.HttpResponseStatus;

import java.io.PrintStream;
import java.nio.file.Path;

public class ScriptOutputAdapter extends ChainWork implements IOutputWork {
	protected Script script = null;
	
	public CommonPath webpath = null;
	public Path file = null;
	protected MimeInfo mime = null;
	protected boolean runonce = false;
	
	public Path getFile() {
		return this.file;
	}
	
	@Override
	public CommonPath getPath() {
		return this.webpath;
	}
	
	@Override
	public void init(Site site, Path file, CommonPath web, String view) throws OperatingContextException {
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
	public void run(TaskContext ctx) throws OperatingContextException {
		if (! this.init) {
			super.init(ctx);
			this.init = true;
		}

		if (this.runonce) {
			super.run(ctx);
			return;
		}

		CountHub.countObjects("dcWebOutScriptCount-" + ctx.getTenant().getAlias(), this);

		Script script = this.getSource();

		if (script == null) {
			ctx.clearExitCode();

			// no source - then run as a SSI
			/* TODO restore
			if (ctx.getSite().getHtmlMode() == HtmlMode.Strict) {
				this.source = xr = new Html();

				xr.with(W3.tag("h1")
					.withText("Unable to parse page error!!")
				);
			}
			else {
				super.run(ctx);
			}
			*/
			
			super.run(ctx);
			return;
		}

		this.runonce = true;
		
		WebController wctrl = (WebController) ctx.getController();

		if (script.getXml().hasAttribute("Badges")) {
			String[] tags = script.getXml().getAttribute("Badges").split(",");
			
			boolean auth = ((tags == null) || ctx.getUserContext().isTagged(tags));
			
			if (! auth) {
				wctrl.sendForbidden();
				super.run(ctx);
				return;
			}
		}
		
		wctrl.getResponse().setHeader("Content-Type", "text/html");
		
		long when = System.currentTimeMillis();
		
		wctrl.getResponse().setHeader("Date", new DateParser().convert(when));
		wctrl.getResponse().setHeader("Last-Modified", new DateParser().convert(when));
		wctrl.getResponse().setHeader("X-UA-Compatible", "IE=Edge,chrome=1");
		
		if ("Server".equalsIgnoreCase(script.getXml().getAttribute("NoCache")))
			wctrl.getResponse().setHeader("Cache-Control", "private, no-store, max-age=0, no-cache, must-revalidate, post-check=0, pre-check=0");
		else
			wctrl.getResponse().setHeader("Cache-Control", "no-cache");
		
		HttpDestStream dest = HttpDestStream.dest().withAsAttachment(false).withAsSendStart(true);
		
		ctx.addVariable("Dest", dest);

		this
				.then(UIUtil.dynamicToWork(ctx, script));
		
		super.run(ctx);
	}
}
